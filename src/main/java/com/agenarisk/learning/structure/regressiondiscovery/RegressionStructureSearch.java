package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
					List<Node> newParents = idsToNodes(graph.getParents(childId), nodesById);
					newParents.add(nodesById.get(parentId));
					LocalScore newScore = scorer.score(nodesById.get(childId), newParents);
					if (newScore == null){
						continue;
					}
					double delta = newScore.getBic() - scoreByNodeId.get(childId).getBic();
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
							&& knowledge.isMoveLegal(graph, parentId, childId, CandidateGraph.MoveType.REVERSE_EDGE)){

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
