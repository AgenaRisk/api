package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regression Structure Discovery: one new, self-contained greedy hill-climbing search over DAGs, scored with
 * {@link RegressionBicScorer}'s decomposable regression-BIC. Not a regression-flavored clone of the legacy engine's
 * five algorithms (HC/Tabu/GES/SaiyanH/MAHC) - it's the one algorithm this feature introduces, running alongside
 * (never touching) the legacy discrete-BIC engine.
 * <br>
 * At each step, every legal single-edge add/remove/reverse move is considered (filtered by acyclicity and
 * {@link RegressionKnowledge} before being scored at all); the best strictly-improving move is applied; this repeats
 * until no improving move exists or a safety iteration cap is hit. Ties are broken deterministically (first
 * encountered in child-id-then-parent-id sorted order), so results are reproducible.
 *
 * @author Eugene Dementiev
 */
public class RegressionStructureSearch {

	private static final double MIN_IMPROVEMENT = 1e-9;

	/**
	 * A node that can never be {@code simulated} (every type except a simulated ContinuousInterval/IntegerInterval)
	 * is assumed to contribute this many effective "states" to a clique's cost when it's itself simulated-continuous
	 * (its own state count is meaningless - dynamic discretization decides that at calculation time, not structure
	 * search time). This is a conservative, fixed stand-in for "however many bins dynamic discretization actually
	 * produces", calibrated empirically against a real case that hung (see {@link #MAX_CLIQUE_COST}'s javadoc) rather
	 * than derived from the engine's real discretization settings, which structure search has no access to anyway.
	 */
	private static final double ASSUMED_SIMULATED_CONTINUOUS_BINS = 20;

	/**
	 * Cap on the estimated cost (product of node "weights" - effective state counts) of the largest clique the
	 * junction tree would need to build for a candidate structure.
	 * <br>
	 * A {@code Ranked} target is treated as an ordinary continuous regression target for fitting purposes (see
	 * {@link com.agenarisk.learning.structure.regression.NodeRole}) - it can be given any number of continuous
	 * parents, fit via a TNormal expression, exactly like a continuous target would. But unlike
	 * {@code ContinuousInterval}/{@code IntegerInterval}, a {@code Ranked} node can never itself be
	 * {@code simulated} - it always needs a real, materialized NPT. Two failure modes were observed in practice from
	 * this mismatch: (1) {@code NPTGenerator.generateNPToverStateCombinations} enumerating every combination of a
	 * materialized target's parents' discretized states, and (2) {@code CoreBNJunctionTree.compile} taking
	 * effectively forever on the resulting network's moral graph - which is a *whole-network* property (how many
	 * nodes get pulled into one clique by sharing children), not something a per-node parent count can capture. Both
	 * are, at bottom, the same thing: some clique in the junction tree ends up needing a table whose size is the
	 * product of its members' state counts. This bounds that product directly via a greedy min-degree elimination
	 * ordering over the moral graph (the standard cheap heuristic for estimating treewidth/clique cost, since exact
	 * treewidth is NP-hard) - BIC scoring during search is pure statistics and has no visibility into this cost, so
	 * it must be enforced as a structural constraint here, independent of scoring.
	 * <br>
	 * Calibrated empirically: reproduced the real hang directly (a 10-node model, a `Ranked` node with 5 parents
	 * including 3 simulated-continuous ones), and confirmed a per-node parent cap alone was insufficient (moved the
	 * hang from NPT generation to junction tree compilation without fixing it) - this whole-graph cost bound is the
	 * next attempt at directly targeting what actually blew up.
	 */
	private static final double MAX_CLIQUE_COST = 5000;

	private final RegressionBicScorer scorer;
	private final RegressionKnowledge knowledge;
	private final int maxParentsPerNodeDefault;
	private final int maxIterations;
	private String progressNodeLabel;
	private boolean progressEnabled = false;

	public RegressionStructureSearch(RegressionBicScorer scorer, RegressionKnowledge knowledge, int maxParentsPerNodeDefault, int maxIterations) {
		this.scorer = scorer;
		this.knowledge = knowledge;
		this.maxParentsPerNodeDefault = maxParentsPerNodeDefault;
		this.maxIterations = maxIterations;
	}

	/**
	 * Nodes whose table will be written as an exact expression rather than fitted. They contribute far less to the
	 * cost of the structure than an ordinary node: there is no NPT to enumerate over their parents' states, and no
	 * parameters to estimate. Declared by the caller, since only the caller knows which relations the data proved.
	 */
	public RegressionStructureSearch withDeterministicNodes(Set<String> nodeIds) {
		deterministicNodeIds.clear();
		if (nodeIds != null){
			deterministicNodeIds.addAll(nodeIds);
		}
		return this;
	}

	private final Set<String> deterministicNodeIds = new HashSet<>();

	/**
	 * How hard to prefer a cheap structure, in BIC units per natural-log unit of family table size.
	 * <br>
	 * The clique budget below is a hard gate, and a gate can only say no - it cannot express that a discrete parent
	 * of a continuous child (a partitioned expression: cheap, and how such a relationship is naturally written) is
	 * preferable to the reverse (a softmax over a continuous parent: expensive to represent, awkward to read). This
	 * term makes that a preference rather than a prohibition: adding a 20-bin continuous parent costs about
	 * {@code ln(20) * WEIGHT} more than a 3-state discrete one, which breaks ties toward the cheaper orientation
	 * while leaving plenty of room for the data to insist otherwise.
	 */
	private static final double COMPLEXITY_PENALTY_WEIGHT = 4;

	/**
	 * The extra structural cost of moving a node from one parent set to another, in BIC units. Positive when the
	 * new parent set is more expensive to represent, so it is subtracted from the move's score.
	 */
	private double complexityPenalty(Node child, List<Node> oldParents, List<Node> newParents) {
		double before = familyCost(child, oldParents);
		double after = familyCost(child, newParents);
		if (before <= 0 || after <= 0){
			return 0;
		}
		return COMPLEXITY_PENALTY_WEIGHT * (Math.log(after) - Math.log(before));
	}

	/** Estimated table size of a node given a parent set - the thing the penalty above is charged on. */
	private double familyCost(Node child, List<Node> parents) {
		double cost = nodeWeight(child);
		for (Node parent : parents){
			cost *= nodeWeight(parent);
		}
		return cost;
	}

	/**
	 * Enables interim progress reporting (via {@link GraphNode#emitProgress}) from the search loop below - off by
	 * default, since this class has no inherent notion of "am I running under athena's progress-output protocol".
	 */
	public void enableProgressReporting(String nodeLabel) {
		this.progressNodeLabel = nodeLabel;
		this.progressEnabled = true;
	}

	/**
	 * Runs the search over the given node set.
	 *
	 * @param nodesById every node eligible to participate in the discovered structure, keyed by id
	 *
	 * @return the search outcome
	 */
	public RegressionStructureResult search(Map<String, Node> nodesById) {

		CandidateGraph graph = new CandidateGraph(nodesById.keySet());

		// Seed required edges first (mirrors the legacy engine's "directed connections force-seeded" idea, evaluated
		// here as a plain in-memory seed step rather than a CSV write).
		for (String key : knowledge.getRequiredEdges()){
			int arrow = key.indexOf("->");
			String parentId = key.substring(0, arrow);
			String childId = key.substring(arrow + 2);
			if (nodesById.containsKey(parentId) && nodesById.containsKey(childId) && !graph.wouldCreateCycle(parentId, childId)){
				graph.addEdge(parentId, childId);
			}
		}

		Map<String, LocalScore> scoreByNodeId = new HashMap<>();
		double totalBic = 0;
		for (String nodeId : graph.getNodeIds()){
			LocalScore score = scoreOrEmptyFallback(graph, nodesById, nodeId);
			scoreByNodeId.put(nodeId, score);
			totalBic += score.getBic();
		}

		int iteration = 0;
		boolean capReached = false;
		long lastProgressEmitMs = System.currentTimeMillis();

		while (true){
			if (iteration >= maxIterations){
				capReached = true;
				break;
			}

			if (progressEnabled){
				long nowMs = System.currentTimeMillis();
				if (nowMs - lastProgressEmitMs >= 1000){
					GraphNode.emitProgress(progressNodeLabel,
							"Searching for structure - iteration " + iteration + " of " + maxIterations
									+ " (BIC " + String.format("%.1f", totalBic) + ")",
							iteration, maxIterations);
					lastProgressEmitMs = nowMs;
				}
			}

			Move best = findBestMove(graph, nodesById, scoreByNodeId);
			if (best == null){
				break;
			}

			applyMove(graph, best);
			for (Map.Entry<String, LocalScore> entry : best.scoreUpdates.entrySet()){
				scoreByNodeId.put(entry.getKey(), entry.getValue());
			}
			totalBic += best.delta;
			iteration++;
		}

		return new RegressionStructureResult(graph, scoreByNodeId, totalBic, iteration, capReached);
	}

	private LocalScore scoreOrEmptyFallback(CandidateGraph graph, Map<String, Node> nodesById, String nodeId) {
		List<Node> parents = idsToNodes(graph.getParents(nodeId), nodesById);
		LocalScore score = scorer.score(nodesById.get(nodeId), parents);
		if (score != null){
			return score;
		}
		// A seeded required edge produced an infeasible fit - fall back to no parents for this node rather than
		// leaving it unscored; the required edge stays in the graph topologically, but this node's own score
		// reflects what's actually fittable.
		return scorer.score(nodesById.get(nodeId), Collections.emptyList());
	}

	private static class Move {

		double delta;
		String parentId;
		String childId;
		CandidateGraph.MoveType type;
		Map<String, LocalScore> scoreUpdates;
	}

	private static boolean isSimulatedContinuous(Node node) {
		return (node.getType() == Node.Type.ContinuousInterval || node.getType() == Node.Type.IntegerInterval) && node.isSimulated();
	}

	/**
	 * @return this node's contribution to a clique's cost - its own state count for anything with a fixed, known
	 * state list, or {@link #ASSUMED_SIMULATED_CONTINUOUS_BINS} for a simulated-continuous node, whose real bin
	 * count is only decided by dynamic discretization at calculation time
	 */
	private double nodeWeight(Node node) {
		// A node written as an exact expression has no NPT enumerated over its parents' states, so it does not
		// carry a table of its own into the clique. Its parents still have to sit together for inference, which is
		// why the moralisation edges it induces are left in place - only its own weight is discounted.
		if (deterministicNodeIds.contains(node.getId())){
			return 1;
		}
		if (isSimulatedContinuous(node)){
			return ASSUMED_SIMULATED_CONTINUOUS_BINS;
		}
		return Math.max(1, node.getStates().size());
	}

	/**
	 * @return the moral graph of {@code graph} as an undirected adjacency map: every DAG edge becomes undirected,
	 * plus every pair of a node's parents becomes connected ("moralization" - the step junction tree construction
	 * itself performs, since a node's parents jointly determine its NPT and so must end up in a clique together)
	 */
	private static Map<String, Set<String>> buildMoralGraph(CandidateGraph graph) {
		Map<String, Set<String>> adjacency = new HashMap<>();
		for (String nodeId : graph.getNodeIds()){
			adjacency.put(nodeId, new HashSet<>());
		}
		for (String childId : graph.getNodeIds()){
			List<String> parents = graph.getParents(childId);
			for (String parentId : parents){
				adjacency.get(childId).add(parentId);
				adjacency.get(parentId).add(childId);
			}
			for (int i = 0; i < parents.size(); i++){
				for (int j = i + 1; j < parents.size(); j++){
					adjacency.get(parents.get(i)).add(parents.get(j));
					adjacency.get(parents.get(j)).add(parents.get(i));
				}
			}
		}
		return adjacency;
	}

	/**
	 * Greedy min-degree elimination ordering over {@code graph}'s moral graph, tracking the largest clique *cost*
	 * (product of member weights, not just member count) seen along the way - the standard cheap heuristic for
	 * estimating junction-tree width/cost, since computing it exactly is NP-hard. At each step, the remaining node
	 * with the fewest remaining neighbours is eliminated; its neighbours are connected to each other ("fill-in",
	 * mirroring what junction tree construction does) before it's removed.
	 *
	 * @return the largest clique cost encountered while eliminating every node in {@code graph}
	 */
	private double estimateMaxCliqueCost(CandidateGraph graph, Map<String, Node> nodesById) {
		Map<String, Set<String>> adjacency = buildMoralGraph(graph);
		Map<String, Double> weightById = new HashMap<>();
		for (String id : graph.getNodeIds()){
			weightById.put(id, nodeWeight(nodesById.get(id)));
		}

		Set<String> remaining = new HashSet<>(graph.getNodeIds());
		double maxCliqueCost = 0;

		while (!remaining.isEmpty()){
			String toEliminate = null;
			int bestDegree = Integer.MAX_VALUE;
			for (String id : remaining){
				int degree = 0;
				for (String neighbor : adjacency.get(id)){
					if (remaining.contains(neighbor)){
						degree++;
					}
				}
				if (degree < bestDegree){
					bestDegree = degree;
					toEliminate = id;
				}
			}

			Set<String> neighbors = new HashSet<>();
			for (String neighbor : adjacency.get(toEliminate)){
				if (remaining.contains(neighbor)){
					neighbors.add(neighbor);
				}
			}

			double cliqueCost = weightById.get(toEliminate);
			for (String neighbor : neighbors){
				cliqueCost *= weightById.get(neighbor);
			}
			maxCliqueCost = Math.max(maxCliqueCost, cliqueCost);

			for (String a : neighbors){
				for (String b : neighbors){
					if (!a.equals(b)){
						adjacency.get(a).add(b);
					}
				}
			}
			remaining.remove(toEliminate);
		}

		return maxCliqueCost;
	}

	/**
	 * @param graph the current candidate graph (before the move)
	 * @param nodesById all nodes by id
	 * @param type the move being considered - {@code REMOVE_EDGE} is never a concern, since it only shrinks the
	 * graph, so this always returns false for it without doing any work
	 * @param parentId the move's parent id
	 * @param childId the move's child id
	 *
	 * @return true if applying this move would push the graph's estimated max clique cost over {@link #MAX_CLIQUE_COST}
	 */
	private boolean wouldExceedCliqueCostBudget(CandidateGraph graph, Map<String, Node> nodesById, CandidateGraph.MoveType type, String parentId, String childId) {
		if (type == CandidateGraph.MoveType.REMOVE_EDGE){
			return false;
		}
		CandidateGraph candidate = new CandidateGraph(graph);
		if (type == CandidateGraph.MoveType.ADD_EDGE){
			candidate.addEdge(parentId, childId);
		}
		else {
			candidate.reverseEdge(parentId, childId);
		}
		double candidateCost = estimateMaxCliqueCost(candidate, nodesById);
		if (candidateCost <= MAX_CLIQUE_COST){
			return false;
		}
		// Over budget - but the question is whether THIS move is what put it there. Required edges are seeded
		// before the search starts and are not optional, so a graph can begin over budget through no choice of the
		// search; judged absolutely, every subsequent move is then rejected for a cost it neither caused nor can
		// reduce, and the search returns the seed graph having never run an iteration. Judged as a delta, moves
		// that leave the expensive region alone still proceed.
		return candidateCost > estimateMaxCliqueCost(graph, nodesById);
	}

	private Move findBestMove(CandidateGraph graph, Map<String, Node> nodesById, Map<String, LocalScore> scoreByNodeId) {

		List<String> nodeIds = new ArrayList<>(graph.getNodeIds());
		Move best = null;

		for (String childId : nodeIds){
			int childParentCount = graph.getParents(childId).size();

			for (String parentId : nodeIds){
				if (parentId.equals(childId)){
					continue;
				}

				if (!graph.hasEdge(parentId, childId)){
					// Candidate ADD_EDGE
					if (childParentCount >= knowledge.maxParentsFor(childId, maxParentsPerNodeDefault)){
						continue;
					}
					if (!knowledge.isMoveLegal(graph, parentId, childId, CandidateGraph.MoveType.ADD_EDGE)){
						continue;
					}
					if (wouldExceedCliqueCostBudget(graph, nodesById, CandidateGraph.MoveType.ADD_EDGE, parentId, childId)){
						continue;
					}
					List<Node> newParents = idsToNodes(graph.getParents(childId), nodesById);
					List<Node> oldParents = idsToNodes(graph.getParents(childId), nodesById);
					newParents.add(nodesById.get(parentId));
					LocalScore newScore = scorer.score(nodesById.get(childId), newParents);
					if (newScore == null){
						continue;
					}
					double delta = newScore.getBic() - scoreByNodeId.get(childId).getBic()
							- complexityPenalty(nodesById.get(childId), oldParents, newParents);
					if (best == null || delta > best.delta + MIN_IMPROVEMENT){
						Move move = new Move();
						move.delta = delta;
						move.parentId = parentId;
						move.childId = childId;
						move.type = CandidateGraph.MoveType.ADD_EDGE;
						move.scoreUpdates = Collections.singletonMap(childId, newScore);
						best = move;
					}
				}
				else {
					// Candidate REMOVE_EDGE
					if (knowledge.isMoveLegal(graph, parentId, childId, CandidateGraph.MoveType.REMOVE_EDGE)){
						List<Node> newParents = idsToNodes(graph.getParents(childId), nodesById);
						newParents.removeIf(n -> n.getId().equals(parentId));
						LocalScore newScore = scorer.score(nodesById.get(childId), newParents);
						if (newScore != null){
							double delta = newScore.getBic() - scoreByNodeId.get(childId).getBic();
							if (best == null || delta > best.delta + MIN_IMPROVEMENT){
								Move move = new Move();
								move.delta = delta;
								move.parentId = parentId;
								move.childId = childId;
								move.type = CandidateGraph.MoveType.REMOVE_EDGE;
								move.scoreUpdates = Collections.singletonMap(childId, newScore);
								best = move;
							}
						}
					}

					// Candidate REVERSE_EDGE (parentId -> childId becomes childId -> parentId)
					int parentOfParentCount = graph.getParents(parentId).size();
					if (parentOfParentCount < knowledge.maxParentsFor(parentId, maxParentsPerNodeDefault)
							&& knowledge.isMoveLegal(graph, parentId, childId, CandidateGraph.MoveType.REVERSE_EDGE)
							&& !wouldExceedCliqueCostBudget(graph, nodesById, CandidateGraph.MoveType.REVERSE_EDGE, parentId, childId)){

						List<Node> childNewParents = idsToNodes(graph.getParents(childId), nodesById);
						childNewParents.removeIf(n -> n.getId().equals(parentId));
						List<Node> parentNewParents = idsToNodes(graph.getParents(parentId), nodesById);
						parentNewParents.add(nodesById.get(childId));

						LocalScore childNewScore = scorer.score(nodesById.get(childId), childNewParents);
						LocalScore parentNewScore = scorer.score(nodesById.get(parentId), parentNewParents);

						if (childNewScore != null && parentNewScore != null){
							double delta = (childNewScore.getBic() + parentNewScore.getBic())
									- (scoreByNodeId.get(childId).getBic() + scoreByNodeId.get(parentId).getBic());
							if (best == null || delta > best.delta + MIN_IMPROVEMENT){
								Move move = new Move();
								move.delta = delta;
								move.parentId = parentId;
								move.childId = childId;
								move.type = CandidateGraph.MoveType.REVERSE_EDGE;
								Map<String, LocalScore> updates = new HashMap<>();
								updates.put(childId, childNewScore);
								updates.put(parentId, parentNewScore);
								move.scoreUpdates = updates;
								best = move;
							}
						}
					}
				}
			}
		}

		return (best != null && best.delta > MIN_IMPROVEMENT) ? best : null;
	}

	private void applyMove(CandidateGraph graph, Move move) {
		switch (move.type){
			case ADD_EDGE:
				graph.addEdge(move.parentId, move.childId);
				break;
			case REMOVE_EDGE:
				graph.removeEdge(move.parentId, move.childId);
				break;
			case REVERSE_EDGE:
				graph.reverseEdge(move.parentId, move.childId);
				break;
		}
	}

	private List<Node> idsToNodes(List<String> ids, Map<String, Node> nodesById) {
		List<Node> nodes = new ArrayList<>(ids.size());
		for (String id : ids){
			nodes.add(nodesById.get(id));
		}
		return nodes;
	}
}
