package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	/**
	 * Joins forbidden-indicator parent state values into a single partition lookup key. A control character keeps it
	 * extremely unlikely to collide with real state labels, which is the only property this needs (same convention as
	 * {@code RegressionDataset}).
	 */
	private static final String PARTITION_KEY_DELIMITER = String.valueOf((char) 1);

	/**
	 * Cap on a categorical node's full conditional table size (parent-state combinations × target states). A manual
	 * NPT is exponential in the parent count, so a dense discovered structure (a node with several high-cardinality
	 * binned parents) can produce a table with billions of cells — which overflows the enumerator's combination count
	 * and/or exhausts memory when allocated, killing the whole candidate. Beyond this cap we skip the node (leaving its
	 * table unchanged) with an advisory rather than attempting the intractable table. 2,000,000 cells (~16 MB of
	 * doubles) is comfortably learnable while well short of the overflow/OOM regime.
	 */
	private static final long MAX_NPT_CELLS = 2_000_000L;

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
		return learn(target, java.util.Collections.emptySet());
	}

	/**
	 * Learns {@code target}'s table, optionally partitioning on (rather than dummy-encoding) specific categorical
	 * parents named in {@code forbidIndicatorParentIds} - the {@code forbidIndicatorEncoding} knowledge constraint.
	 * Partitioning a parent fits an independent sub-model per state combination of the forbidden parents, giving those
	 * parents a full interaction with the rest of the model rather than a single pooled additive (dummy) effect - at
	 * the cost of splitting the data. Parents not named here are still main-effects dummy-encoded within each
	 * partition's sub-fit. The output is a fully-specified manual NPT either way.
	 *
	 * @param target the categorical node to learn; must already have passed {@link RegressionEligibility#evaluate}
	 * @param forbidIndicatorParentIds ids of parents to partition on instead of dummy-encoding; ids that are not
	 * actually categorical parents of {@code target} are ignored
	 *
	 * @return the learning outcome
	 */
	public NodeLearningResult learn(Node target, Set<String> forbidIndicatorParentIds) {

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

		// A single-state (or empty) target has no distribution to learn - multinomial
		// logistic regression requires at least two classes. Skip it with a warning
		// rather than throwing and failing the whole run (e.g. a variable that is
		// constant in this data slice, or a numeric column binned to a single bin).
		if (targetStates.size() < 2){
			return new NodeLearningResult(target, true, "Node '" + target.getId() + "' has fewer than two states in this data, so no distribution can be learned for it; leaving its table unchanged", null, null, 0, Double.NaN, false);
		}

		// A full manual NPT is exponential in the parent count. Compute its size with long arithmetic (the enumerator
		// counts combinations in an int, which would silently overflow) and, if it exceeds what we can tractably build,
		// skip the node instead of overflowing/OOMing on an intractable table. This is the common failure for dense
		// discovered structures (a node with several high-cardinality binned parents).
		long combinations = 1L;
		for (Node parent : parents){
			combinations *= Math.max(1, statesOf(parent).size());
			if (combinations * (long) targetStates.size() > MAX_NPT_CELLS){
				return new NodeLearningResult(target, true,
						"Node '" + target.getId() + "' has too many parents (" + parents.size() + ") to learn a full "
						+ "conditional table from this data — its table would need more than " + MAX_NPT_CELLS
						+ " cells. Leaving its table unchanged; use a sparser search or coarser bins if you need it learned.",
						null, null, 0, Double.NaN, false);
			}
		}

		List<Node> partitionParents = new ArrayList<>();
		List<Node> encodedParents = new ArrayList<>();
		for (Node parent : parents){
			if (forbidIndicatorParentIds.contains(parent.getId())){
				partitionParents.add(parent);
			}
			else {
				encodedParents.add(parent);
			}
		}

		if (partitionParents.isEmpty()){
			return learnSingleFit(target, parents, targetStates);
		}
		return learnPartitioned(target, parents, targetStates, partitionParents, encodedParents);
	}

	/**
	 * The original single-fit path: one main-effects multinomial logit over all (dummy-encoded) parents, evaluated at
	 * every parent-state combination to populate the NPT.
	 */
	private NodeLearningResult learnSingleFit(Node target, List<Node> parents, List<String> targetStates) {
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

	/**
	 * The {@code forbidIndicatorEncoding} path: fits one independent sub-model per state combination of
	 * {@code partitionParents}, each a main-effects multinomial logit over {@code encodedParents}, then assembles the
	 * full NPT over all of {@code parents}. A partition with no rows of its own falls back to the target's global
	 * marginal (or a uniform distribution if the target has no usable rows at all), so the NPT is always complete.
	 */
	private NodeLearningResult learnPartitioned(Node target, List<Node> parents, List<String> targetStates, List<Node> partitionParents, List<Node> encodedParents) {

		List<String> encodedParentIds = encodedParents.stream().map(Node::getId).collect(Collectors.toList());
		List<List<String>> encodedParentStates = encodedParents.stream().map(this::statesOf).collect(Collectors.toList());

		double[] fallbackProbs = globalMarginal(target, targetStates);

		List<PartitionEnumerator.Combination> partitionCombinations = PartitionEnumerator.enumerate(partitionParents);
		Map<String, MultinomialLogisticRegression.Result> fitsByPartition = new HashMap<>();
		int totalN = 0;
		boolean allConverged = true;
		for (PartitionEnumerator.Combination partitionCombination : partitionCombinations){
			RegressionDataset.CategoricalSelection selection = dataset.selectCategoricalRows(target.getId(), targetStates, encodedParentIds, encodedParentStates, partitionCombination);
			if (selection.getN() == 0){
				allConverged = false;
				continue;
			}
			MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);
			fitsByPartition.put(partitionKey(partitionCombination, partitionParents), fit);
			totalN += fit.getN();
			allConverged = allConverged && fit.isConverged();
		}

		if (totalN == 0){
			return new NodeLearningResult(target, true, "No usable data found for node '" + target.getId() + "' (and/or its parents); leaving its table unchanged", null, null, 0, Double.NaN, false);
		}

		List<PartitionEnumerator.Combination> combinations = PartitionEnumerator.enumerate(parents);
		double[][] npt = new double[combinations.size()][];
		for (int c = 0; c < combinations.size(); c++){
			PartitionEnumerator.Combination combination = combinations.get(c);
			MultinomialLogisticRegression.Result fit = fitsByPartition.get(partitionKey(combination, partitionParents));
			if (fit == null){
				npt[c] = fallbackProbs.clone();
				continue;
			}
			List<String> encodedValues = encodedParentIds.stream().map(combination::getState).collect(Collectors.toList());
			double[] dummyRow = CategoricalDummyEncoder.encode(encodedValues, encodedParentStates);
			npt[c] = fit.predictProbabilities(dummyRow);
		}

		return new NodeLearningResult(target, false, null, parents, npt, totalN, Double.NaN, allConverged);
	}

	/**
	 * The target's unconditional class distribution (intercept-only fit over all usable rows), used to fill partitions
	 * that have no rows of their own. Falls back to a uniform distribution if the target has no usable rows at all.
	 */
	private double[] globalMarginal(Node target, List<String> targetStates) {
		RegressionDataset.CategoricalSelection selection = dataset.selectCategoricalRows(target.getId(), targetStates, new ArrayList<>(), new ArrayList<>());
		if (selection.getN() == 0){
			double[] uniform = new double[targetStates.size()];
			java.util.Arrays.fill(uniform, 1.0 / targetStates.size());
			return uniform;
		}
		MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);
		return fit.predictProbabilities(new double[0]);
	}

	/**
	 * Canonical key for a partition's state assignment over {@code partitionParents}, so a full parent-state
	 * combination can look up the sub-fit for its forbidden-parent sub-combination.
	 */
	private String partitionKey(PartitionEnumerator.Combination combination, List<Node> partitionParents) {
		StringBuilder sb = new StringBuilder();
		for (Node parent : partitionParents){
			sb.append(combination.getState(parent.getId())).append(PARTITION_KEY_DELIMITER);
		}
		return sb.toString();
	}

	private List<String> statesOf(Node node) {
		return node.getStates().stream().map(State::getLabel).collect(Collectors.toList());
	}
}
