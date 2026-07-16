package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Learns a categorical target node's table (Boolean, Labelled, or DiscreteReal) from data via ridge-regularized
 * multinomial logistic regression over its categorical parents, as the categorical analog of
 * {@link ContinuousRegressionLearner}.
 * <br>
 * Unlike the continuous learner, this doesn't need a multi-tier sparse-partition fallback: because the model is
 * fitted once as main effects across all parents (not one independent fit per parent-state combination), and
 * because the ridge penalty keeps the fit well-posed even when a particular parent state was never observed, a
 * single fit already degrades gracefully under sparse data - an unobserved state's coefficient simply shrinks
 * towards 0 (behaving like the reference state) rather than the fit becoming infeasible.
 * <br>
 * The fitted model is evaluated at every combination of the parents' declared states (not just the combinations
 * that appear in the data) to populate a complete manual NPT, since a categorical node's table must be fully
 * specified regardless of data coverage.
 * <br>
 * This learner assumes {@code target} has already passed {@link RegressionEligibility#evaluate} - i.e. it has no
 * continuous parents. It re-checks defensively but does not itself decide eligibility.
 *
 * @author Eugene Dementiev
 */
public class CategoricalRegressionLearner {

	/**
	 * Learning outcome for one categorical target node.
	 */
	public static class NodeLearningResult {

		private final Node target;
		private final boolean skipped;
		private final String skipReason;
		private final List<Node> parents;
		private final double[][] npt;
		private final int n;
		private final double pseudoR2;
		private final boolean converged;

		private NodeLearningResult(Node target, boolean skipped, String skipReason, List<Node> parents, double[][] npt, int n, double pseudoR2, boolean converged) {
			this.target = target;
			this.skipped = skipped;
			this.skipReason = skipReason;
			this.parents = parents;
			this.npt = npt;
			this.n = n;
			this.pseudoR2 = pseudoR2;
			this.converged = converged;
		}

		public Node getTarget() {
			return target;
		}

		public boolean isSkipped() {
			return skipped;
		}

		public String getSkipReason() {
			return skipReason;
		}

		/**
		 * @return the parents this table was conditioned on, in the same order as {@code target.getParents()}
		 * (link-insertion order) - this is the order {@code Node.setTableColumns} independently assumes
		 */
		public List<Node> getParents() {
			return parents;
		}

		/**
		 * @return npt[combinationIndex][targetStateIndex], ready for {@code Node.setTableColumns}; combinations are
		 * enumerated in the same row-major order as {@link PartitionEnumerator}
		 */
		public double[][] getNpt() {
			return npt;
		}

		public int getN() {
			return n;
		}

		public double getPseudoR2() {
			return pseudoR2;
		}

		public boolean isConverged() {
			return converged;
		}
	}

	private final RegressionDataset dataset;
	private final double ridgeLambda;

	public CategoricalRegressionLearner(RegressionDataset dataset) {
		this(dataset, MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA);
	}

	public CategoricalRegressionLearner(RegressionDataset dataset, double ridgeLambda) {
		this.dataset = dataset;
		this.ridgeLambda = ridgeLambda;
	}

	/**
	 * Learns {@code target}'s table from the data this learner was constructed with.
	 * <br>
	 * Does not itself write anything back to the model - see {@link CategoricalTableWriter}.
	 *
	 * @param target the categorical node to learn; must already have passed {@link RegressionEligibility#evaluate}
	 *
	 * @return the learning outcome
	 */
	public NodeLearningResult learn(Node target) {

		RegressionEligibility.Decision eligibility = RegressionEligibility.evaluate(target);
		if (!eligibility.isEligible()){
			return new NodeLearningResult(target, true, eligibility.getReason(), null, null, 0, Double.NaN, false);
		}

		if (NodeRole.of(target) != NodeRole.CATEGORICAL){
			return new NodeLearningResult(target, true, "Node '" + target.getId() + "' is continuous; this learner only handles categorical targets", null, null, 0, Double.NaN, false);
		}

		// Parent order matters here: Node.setTableColumns derives it independently from target.getParents(), so we
		// must enumerate combinations in that exact same order rather than choosing our own.
		List<Node> parents = new ArrayList<>(target.getParents());
		for (Node parent : parents){
			if (NodeRole.of(parent) != NodeRole.CATEGORICAL){
				// Should already have been caught by RegressionEligibility - defensive only
				return new NodeLearningResult(target, true, "Node '" + target.getId() + "' has a continuous parent '" + parent.getId() + "'; this should have been caught earlier", null, null, 0, Double.NaN, false);
			}
		}

		List<String> targetStates = statesOf(target);
		List<String> parentIds = parents.stream().map(Node::getId).collect(Collectors.toList());
		List<List<String>> parentStates = parents.stream().map(this::statesOf).collect(Collectors.toList());

		RegressionDataset.CategoricalSelection selection = dataset.selectCategoricalRows(target.getId(), targetStates, parentIds, parentStates);

		if (selection.getN() == 0){
			return new NodeLearningResult(target, true, "No usable data found for node '" + target.getId() + "' (and/or its parents); leaving its table unchanged", null, null, 0, Double.NaN, false);
		}

		MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);

		List<PartitionEnumerator.Combination> combinations = PartitionEnumerator.enumerate(parents);
		double[][] npt = new double[combinations.size()][];
		for (int c = 0; c < combinations.size(); c++){
			List<String> valuesByParent = parentIds.stream().map(combinations.get(c)::getState).collect(Collectors.toList());
			double[] dummyRow = CategoricalDummyEncoder.encode(valuesByParent, parentStates);
			npt[c] = fit.predictProbabilities(dummyRow);
		}

		return new NodeLearningResult(target, false, null, parents, npt, selection.getN(), fit.getPseudoR2(), fit.isConverged());
	}

	private List<String> statesOf(Node node) {
		return node.getStates().stream().map(State::getLabel).collect(Collectors.toList());
	}
}
