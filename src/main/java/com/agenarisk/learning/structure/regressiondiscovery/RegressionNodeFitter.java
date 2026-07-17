package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.CategoricalTableWriter;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.LogisticExpressionTableWriter;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.NodeRole;
import com.agenarisk.learning.structure.regression.RegressionTableWriter;

/**
 * Fits a single node's table against its ALREADY-LINKED parents (structure fixed) and writes the result back,
 * dispatching to the same three learners the Slice 2 regression package already provides: continuous target ->
 * {@link ContinuousRegressionLearner}, categorical target with only categorical parents ->
 * {@link CategoricalRegressionLearner}, categorical target with any continuous parent ->
 * {@link LogisticRegressionLearner}.
 * <br>
 * This is the one piece of "fit a target against a given parent set" logic shared between
 * {@link RegressionModelMaterializer} (writing back the winning structure a {@link RegressionStructureSearch} run
 * found) and the standalone parameter-learning path (structure already fixed, e.g. from an imported model) - so
 * "search discovers structure, standalone node bakes tables for fixed structure" are provably doing the same fit.
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

		private NodeFitOutcome(String nodeId, boolean skipped, String skipReason) {
			this.nodeId = nodeId;
			this.skipped = skipped;
			this.skipReason = skipReason;
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
	}

	private final ContinuousRegressionLearner continuousLearner;
	private final CategoricalRegressionLearner categoricalLearner;
	private final LogisticRegressionLearner logisticLearner;

	public RegressionNodeFitter(ContinuousRegressionLearner continuousLearner, CategoricalRegressionLearner categoricalLearner, LogisticRegressionLearner logisticLearner) {
		this.continuousLearner = continuousLearner;
		this.categoricalLearner = categoricalLearner;
		this.logisticLearner = logisticLearner;
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
			boolean hasContinuousParent = node.getParents().stream().anyMatch(parent -> NodeRole.of(parent) == NodeRole.CONTINUOUS);

			if (hasContinuousParent){
				LogisticRegressionLearner.NodeLearningResult result = logisticLearner.learn(node);
				if (result.isSkipped()){
					return new NodeFitOutcome(node.getId(), true, result.getSkipReason());
				}
				LogisticExpressionTableWriter.apply(result);
				return new NodeFitOutcome(node.getId(), false, null);
			}

			CategoricalRegressionLearner.NodeLearningResult result = categoricalLearner.learn(node);
			if (result.isSkipped()){
				return new NodeFitOutcome(node.getId(), true, result.getSkipReason());
			}
			CategoricalTableWriter.apply(result);
			return new NodeFitOutcome(node.getId(), false, null);
		}

		ContinuousRegressionLearner.NodeLearningResult result = continuousLearner.learn(node);
		if (result.isSkipped()){
			return new NodeFitOutcome(node.getId(), true, result.getSkipReason());
		}
		RegressionTableWriter.apply(result);
		return new NodeFitOutcome(node.getId(), false, null);
	}
}
