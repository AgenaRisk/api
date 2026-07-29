package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.CategoricalTableWriter;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.LogisticExpressionTableWriter;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.NodeRole;
import com.agenarisk.learning.structure.regression.PartitionEnumerator;
import com.agenarisk.learning.structure.regression.RegressionTableWriter;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fits a single node's table against its ALREADY-LINKED parents (structure fixed) and writes the result back,
 * dispatching to the same three learners the Slice 2 regression package already provides: continuous target ->
 * {@link ContinuousRegressionLearner}, categorical target with only categorical parents ->
 * {@link CategoricalRegressionLearner}, categorical target with any continuous parent ->
 * {@link LogisticRegressionLearner}.
 * <br>
 * This is the one piece of "fit a target against a given parent set" logic shared between
 * {@link RegressionModelMaterializer} (writing back the winning structure a {@link RegressionStructureSearch} run
 * found) and the standalone parameter-learning path (structure already fixed, e.g. from an imported model, via
 * {@code RegressionParameterLearningNode}) - so "search discovers structure" and "standalone node bakes tables for
 * fixed structure" are provably doing the same fit and reporting the same diagnostic detail.
 *
 * @author Eugene Dementiev
 */
public class RegressionNodeFitter {

	/**
	 * Outcome of fitting and writing one node's table.
	 */
	public static class NodeFitOutcome {

		private final String nodeId;
		private final boolean skipped;
		private final String skipReason;
		private final JSONObject detail;

		private NodeFitOutcome(String nodeId, boolean skipped, String skipReason, JSONObject detail) {
			this.nodeId = nodeId;
			this.skipped = skipped;
			this.skipReason = skipReason;
			this.detail = detail;
		}

		public String getNodeId() {
			return nodeId;
		}

		public boolean isSkipped() {
			return skipped;
		}

		public String getSkipReason() {
			return skipReason;
		}

		/**
		 * @return diagnostic detail for this fit (continuous: {@code partitions} array with per-partition
		 * n/r2/fitSource; categorical-only-parents: {@code n}/{@code pseudoR2}/{@code converged} plus a
		 * {@code combinations} array with per-combination probabilities; categorical-with-continuous-parent:
		 * {@code n}/{@code pseudoR2}/{@code converged}/{@code expression}), or null if the fit was skipped
		 */
		public JSONObject getDetail() {
			return detail;
		}
	}

	private final ContinuousRegressionLearner continuousLearner;
	private final CategoricalRegressionLearner categoricalLearner;
	private final LogisticRegressionLearner logisticLearner;
	private final RegressionKnowledge knowledge;

	/**
	 * Constructs a fitter with no regression-role/indicator-encoding constraints - every categorical node uses the
	 * default representation (manual NPT for categorical-only parents, {@code MultinomialLogit} expression when a
	 * continuous parent is present). Used by the standalone parameter-learning path, which carries no knowledge object.
	 */
	public RegressionNodeFitter(ContinuousRegressionLearner continuousLearner, CategoricalRegressionLearner categoricalLearner, LogisticRegressionLearner logisticLearner) {
		this(continuousLearner, categoricalLearner, logisticLearner, new RegressionKnowledge());
	}

	/**
	 * Constructs a fitter that honours {@code knowledge}'s regression-role ({@code forceRegressionRole} /
	 * {@code forbidRegressionRole}) and {@code forbidIndicatorEncoding} constraints when choosing each categorical
	 * node's representation and encoding. Edge/tier constraints in {@code knowledge} are not this class's concern (the
	 * structure search enforces those); only the per-node representation constraints are read here.
	 */
	public RegressionNodeFitter(ContinuousRegressionLearner continuousLearner, CategoricalRegressionLearner categoricalLearner, LogisticRegressionLearner logisticLearner, RegressionKnowledge knowledge) {
		this.continuousLearner = continuousLearner;
		this.categoricalLearner = categoricalLearner;
		this.logisticLearner = logisticLearner;
		this.knowledge = knowledge;
	}

	/**
	 * Fits {@code node}'s table against its current parents (as already linked on the model) and writes it back.
	 *
	 * @param node the node to fit; must already be linked to its intended parents
	 *
	 * @return the fit outcome
	 *
	 * @throws Exception if the learned table/expression can't be written back onto the node
	 */
	public NodeFitOutcome fitAndWrite(Node node) throws Exception {

		if (NodeRole.of(node) == NodeRole.CATEGORICAL){
			return fitAndWriteCategorical(node);
		}

		ContinuousRegressionLearner.NodeLearningResult result = continuousLearner.learn(node);
		if (result.isSkipped()){
			return new NodeFitOutcome(node.getId(), true, result.getSkipReason(), null);
		}
		RegressionTableWriter.apply(result);

		JSONObject detail = new JSONObject();
		JSONArray jPartitions = new JSONArray();
		for (ContinuousRegressionLearner.PartitionResult pr : result.getPartitionResults()){
			JSONObject jPartition = new JSONObject();
			if (pr.getCombination() != null){
				jPartition.put("states", pr.getCombination().getStatesByNodeId());
			}
			jPartition.put("n", pr.getN());
			jPartition.put("r2", Double.isNaN(pr.getR2()) ? JSONObject.NULL : pr.getR2());
			jPartition.put("fitSource", pr.getFitSource().name());
			jPartitions.put(jPartition);
		}
		detail.put("partitions", jPartitions);
		return new NodeFitOutcome(node.getId(), false, null, detail);
	}

	/**
	 * Fits and writes a categorical target, choosing its representation per the {@link RegressionKnowledge}
	 * regression-role and indicator-encoding constraints:
	 * <ul>
	 * <li>role: {@code forceRegressionRole} keeps it a live {@code MultinomialLogit} expression;
	 * {@code forbidRegressionRole} forces a materialised manual NPT (impossible against a continuous parent, so such a
	 * node is skipped with an advisory); otherwise the default applies - an expression when a continuous parent is
	 * present, a manual NPT otherwise. If a node is contradictorily in both sets, the restriction wins (manual NPT).</li>
	 * <li>indicator encoding: categorical parents named in {@code forbidIndicatorEncoding} are partitioned on rather
	 * than dummy-encoded, in whichever representation was chosen.</li>
	 * </ul>
	 */
	private NodeFitOutcome fitAndWriteCategorical(Node node) throws Exception {

		boolean hasContinuousParent = node.getParents().stream().anyMatch(parent -> NodeRole.of(parent) == NodeRole.CONTINUOUS);

		java.util.Set<String> forbidIndicator = node.getParents().stream()
				.map(Node::getId)
				.filter(knowledge::isIndicatorEncodingForbidden)
				.collect(Collectors.toSet());

		boolean forceExpression = knowledge.mustUseRegressionRole(node.getId());
		boolean forbidExpression = knowledge.mustNotUseRegressionRole(node.getId());

		boolean wantExpression;
		if (forbidExpression){
			// Restriction wins even if forceRegressionRole is also (contradictorily) set.
			wantExpression = false;
		}
		else if (forceExpression){
			wantExpression = true;
		}
		else {
			wantExpression = hasContinuousParent;
		}

		if (!wantExpression && hasContinuousParent){
			String reason = "Node '" + node.getId() + "' is categorical with continuous parent(s), but forbidRegressionRole "
					+ "requires a manual table - which can't represent a learned distribution over a "
					+ "dynamically-discretised continuous parent. Leaving its table unchanged; remove the constraint to "
					+ "learn it as a MultinomialLogit expression, or remodel the edge (e.g. an explicit discretised parent).";
			return new NodeFitOutcome(node.getId(), true, reason, null);
		}

		if (wantExpression){
			LogisticRegressionLearner.NodeLearningResult result = logisticLearner.learn(node, forbidIndicator, true);
			if (result.isSkipped()){
				return new NodeFitOutcome(node.getId(), true, result.getSkipReason(), null);
			}
			LogisticExpressionTableWriter.apply(result);

			JSONObject detail = new JSONObject();
			detail.put("n", result.getN());
			detail.put("pseudoR2", Double.isNaN(result.getPseudoR2()) ? JSONObject.NULL : result.getPseudoR2());
			detail.put("converged", result.isConverged());
			if (result.getPartitionParents().isEmpty()){
				detail.put("expression", result.getExpression());
			}
			else {
				detail.put("partitionedOn", new JSONArray(result.getPartitionParents().stream().map(Node::getId).collect(Collectors.toList())));
				detail.put("expressions", new JSONArray(result.getExpressions()));
			}
			return new NodeFitOutcome(node.getId(), false, null, detail);
		}

		CategoricalRegressionLearner.NodeLearningResult result = categoricalLearner.learn(node, forbidIndicator);
		if (result.isSkipped()){
			return new NodeFitOutcome(node.getId(), true, result.getSkipReason(), null);
		}
		CategoricalTableWriter.apply(result);

		JSONObject detail = new JSONObject();
		detail.put("n", result.getN());
		detail.put("pseudoR2", Double.isNaN(result.getPseudoR2()) ? JSONObject.NULL : result.getPseudoR2());
		detail.put("converged", result.isConverged());
		if (!forbidIndicator.isEmpty()){
			detail.put("partitionedOn", new JSONArray(node.getParents().stream().map(Node::getId).filter(forbidIndicator::contains).collect(Collectors.toList())));
		}

		List<PartitionEnumerator.Combination> combinations = PartitionEnumerator.enumerate(result.getParents());
		List<String> targetStates = node.getStates().stream().map(State::getLabel).collect(Collectors.toList());
		JSONArray jCombinations = new JSONArray();
		for (int c = 0; c < combinations.size(); c++){
			JSONObject jCombination = new JSONObject();
			jCombination.put("states", combinations.get(c).getStatesByNodeId());
			JSONObject jProbabilities = new JSONObject();
			for (int s = 0; s < targetStates.size(); s++){
				jProbabilities.put(targetStates.get(s), result.getNpt()[c][s]);
			}
			jCombination.put("probabilities", jProbabilities);
			jCombinations.put(jCombination);
		}
		detail.put("combinations", jCombinations);
		return new NodeFitOutcome(node.getId(), false, null, detail);
	}
}
