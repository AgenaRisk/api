package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Learns a categorical target node's table from data via ridge-regularized multinomial logistic regression, for the
 * case a plain {@link CategoricalRegressionLearner} can't handle: a categorical target with at least one continuous
 * parent (mixed with any number of categorical parents).
 * <br>
 * Unlike {@link CategoricalRegressionLearner} (which bakes a fully-specified manual NPT, since its parents are all
 * categorical and every combination can be enumerated up front), this learner persists a single, non-partitioned
 * {@code MultinomialLogit(...)} expression: continuous parents enter each linear predictor directly by node id,
 * categorical parents are dummy-encoded via {@code Indicator(parentId, "state")} terms. This mirrors the K-1
 * reference-class convention {@link MultinomialLogisticRegression} already uses internally, so no reindexing is
 * needed between the fit and the expression text.
 * <br>
 * This learner only handles the mixed/continuous-parent case - a categorical target with categorical-only parents
 * is {@link CategoricalRegressionLearner}'s job, and remains fully unaffected by this class's existence.
 *
 * @author Eugene Dementiev
 */
public class LogisticRegressionLearner {

	/**
	 * Learning outcome for one categorical target node.
	 */
	public static class NodeLearningResult {

		private final Node target;
		private final boolean skipped;
		private final String skipReason;
		private final List<Node> continuousParents;
		private final List<Node> categoricalParents;
		private final List<String> expressions;
		private final List<Node> partitionParents;
		private final int n;
		private final double pseudoR2;
		private final boolean converged;

		private NodeLearningResult(Node target, boolean skipped, String skipReason, List<Node> continuousParents, List<Node> categoricalParents,
				List<String> expressions, List<Node> partitionParents, int n, double pseudoR2, boolean converged) {
			this.target = target;
			this.skipped = skipped;
			this.skipReason = skipReason;
			this.continuousParents = continuousParents;
			this.categoricalParents = categoricalParents;
			this.expressions = expressions;
			this.partitionParents = partitionParents;
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

		public List<Node> getContinuousParents() {
			return continuousParents;
		}

		public List<Node> getCategoricalParents() {
			return categoricalParents;
		}

		/**
		 * @return the first (or only) learned {@code MultinomialLogit(...)} expression, or null if skipped. When
		 * {@link #getPartitionParents()} is empty this is the node's single expression (apply via
		 * {@link Node#setTableFunction(String)}); when it is non-empty this is only the first partition's expression -
		 * use {@link #getExpressions()} with {@link Node#setTableFunctions(List, List)} instead.
		 */
		public String getExpression() {
			return (expressions == null || expressions.isEmpty()) ? null : expressions.get(0);
		}

		/**
		 * @return one {@code MultinomialLogit(...)} expression per partition combination (a single-element list when the
		 * node is not partitioned, i.e. {@link #getPartitionParents()} is empty), or null if skipped
		 */
		public List<String> getExpressions() {
			return expressions;
		}

		/**
		 * @return the categorical parents this node's table is partitioned on (the {@code forbidIndicatorEncoding}
		 * parents), enumerated in the same order as {@link #getExpressions()}; empty when the node is a single
		 * non-partitioned expression
		 */
		public List<Node> getPartitionParents() {
			return partitionParents;
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

	public LogisticRegressionLearner(RegressionDataset dataset) {
		this(dataset, MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA);
	}

	public LogisticRegressionLearner(RegressionDataset dataset, double ridgeLambda) {
		this.dataset = dataset;
		this.ridgeLambda = ridgeLambda;
	}

	/**
	 * Learns {@code target}'s table from the data this learner was constructed with.
	 * <br>
	 * Does not itself write anything back to the model - see {@link LogisticExpressionTableWriter}.
	 *
	 * @param target the categorical node to learn; must have at least one continuous parent (a categorical-only-parent
	 * target is declined - use {@link CategoricalRegressionLearner} for that case instead)
	 *
	 * @return the learning outcome
	 */
	public NodeLearningResult learn(Node target) {
		return learn(target, java.util.Collections.emptySet(), false);
	}

	/**
	 * Learns {@code target}'s table as a {@code MultinomialLogit} expression, honouring two knowledge constraints:
	 * <ul>
	 * <li>{@code allowCategoricalOnlyParents} - normally this learner declines a categorical-only-parents target
	 * (that's {@link CategoricalRegressionLearner}'s job, baking a manual NPT); when true (the
	 * {@code forceRegressionRole} case) it instead emits a {@code MultinomialLogit} expression over the dummy-encoded
	 * categorical parents, keeping the node a live expression rather than a materialised table.</li>
	 * <li>{@code forbidIndicatorParentIds} - categorical parents named here are partitioned on (a separate
	 * {@code MultinomialLogit} expression per state combination, applied via {@link Node#setTableFunctions(List, List)})
	 * rather than dummy-encoded as {@code Indicator(...)} terms within a single expression.</li>
	 * </ul>
	 *
	 * @param target the categorical node to learn
	 * @param forbidIndicatorParentIds ids of categorical parents to partition on instead of dummy-encoding; ids that
	 * are not actually categorical parents of {@code target} are ignored
	 * @param allowCategoricalOnlyParents if true, a categorical-only-parents target is learned as an expression here
	 * rather than declined
	 *
	 * @return the learning outcome
	 */
	public NodeLearningResult learn(Node target, Set<String> forbidIndicatorParentIds, boolean allowCategoricalOnlyParents) {

		RegressionEligibility.Decision eligibility = RegressionEligibility.evaluate(target, true);
		if (!eligibility.isEligible()){
			return skip(target, eligibility.getReason());
		}

		if (NodeRole.of(target) != NodeRole.CATEGORICAL){
			return skip(target, "Node '" + target.getId() + "' is continuous; this learner only handles categorical targets");
		}

		Set<Node> allParents = target.getParents();
		List<Node> continuousParents = allParents.stream()
				.filter(parent -> NodeRole.of(parent) == NodeRole.CONTINUOUS)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());
		List<Node> categoricalParents = allParents.stream()
				.filter(parent -> NodeRole.of(parent) == NodeRole.CATEGORICAL)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());

		if (continuousParents.isEmpty() && !allowCategoricalOnlyParents){
			return skip(target, "Node '" + target.getId() + "' has no continuous parents; use CategoricalRegressionLearner instead");
		}

		List<String> targetStates = statesOf(target);

		// A single-state (or empty) target has no distribution to learn - logistic
		// regression requires at least two classes. Skip with a warning rather than
		// throwing and failing the whole run.
		if (targetStates.size() < 2){
			return skip(target, "Node '" + target.getId() + "' has fewer than two states in this data, so no distribution can be learned for it; leaving its table unchanged");
		}

		List<Node> partitionParents = categoricalParents.stream()
				.filter(parent -> forbidIndicatorParentIds.contains(parent.getId()))
				.collect(Collectors.toList());
		List<Node> encodedCategoricalParents = categoricalParents.stream()
				.filter(parent -> !forbidIndicatorParentIds.contains(parent.getId()))
				.collect(Collectors.toList());

		if (partitionParents.isEmpty()){
			return learnSingle(target, targetStates, continuousParents, categoricalParents);
		}
		return learnPartitioned(target, targetStates, continuousParents, categoricalParents, partitionParents, encodedCategoricalParents);
	}

	/**
	 * The non-partitioned path: one {@code MultinomialLogit} expression over the continuous parents (direct) and all
	 * categorical parents (dummy-encoded as {@code Indicator(...)} terms).
	 */
	private NodeLearningResult learnSingle(Node target, List<String> targetStates, List<Node> continuousParents, List<Node> categoricalParents) {
		List<String> continuousParentIds = continuousParents.stream().map(Node::getId).collect(Collectors.toList());
		List<String> categoricalParentIds = categoricalParents.stream().map(Node::getId).collect(Collectors.toList());
		List<List<String>> categoricalParentStates = categoricalParents.stream().map(this::statesOf).collect(Collectors.toList());

		RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
				target.getId(), targetStates, continuousParentIds, categoricalParentIds, categoricalParentStates);

		if (selection.getN() == 0){
			return skip(target, "No usable data found for node '" + target.getId() + "' (and/or its parents); leaving its table unchanged");
		}

		MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);
		String expression = buildMultinomialLogitExpression(fit, continuousParents, categoricalParents, categoricalParentStates);

		List<String> expressions = new ArrayList<>();
		expressions.add(expression);
		return new NodeLearningResult(target, false, null, continuousParents, categoricalParents, expressions,
				java.util.Collections.emptyList(), selection.getN(), fit.getPseudoR2(), fit.isConverged());
	}

	/**
	 * The {@code forbidIndicatorEncoding} path: one {@code MultinomialLogit} expression per state combination of
	 * {@code partitionParents}, each fitted over the continuous parents and the remaining (dummy-encoded) categorical
	 * parents, applied via {@link Node#setTableFunctions(List, List)}. A partition with no rows falls back to the
	 * target's global marginal (constant linear predictors), so every partition is specified.
	 */
	private NodeLearningResult learnPartitioned(Node target, List<String> targetStates, List<Node> continuousParents,
			List<Node> categoricalParents, List<Node> partitionParents, List<Node> encodedCategoricalParents) {

		List<String> continuousParentIds = continuousParents.stream().map(Node::getId).collect(Collectors.toList());
		List<String> encodedParentIds = encodedCategoricalParents.stream().map(Node::getId).collect(Collectors.toList());
		List<List<String>> encodedParentStates = encodedCategoricalParents.stream().map(this::statesOf).collect(Collectors.toList());

		String fallbackExpression = globalMarginalExpression(target, targetStates);

		List<PartitionEnumerator.Combination> partitionCombinations = PartitionEnumerator.enumerate(partitionParents);
		List<String> expressions = new ArrayList<>();
		int totalN = 0;
		boolean allConverged = true;
		for (PartitionEnumerator.Combination partitionCombination : partitionCombinations){
			RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
					target.getId(), targetStates, continuousParentIds, encodedParentIds, encodedParentStates, partitionCombination);
			if (selection.getN() == 0){
				expressions.add(fallbackExpression);
				allConverged = false;
				continue;
			}
			MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);
			expressions.add(buildMultinomialLogitExpression(fit, continuousParents, encodedCategoricalParents, encodedParentStates));
			totalN += fit.getN();
			allConverged = allConverged && fit.isConverged();
		}

		if (totalN == 0){
			return skip(target, "No usable data found for node '" + target.getId() + "' (and/or its parents); leaving its table unchanged");
		}

		return new NodeLearningResult(target, false, null, continuousParents, categoricalParents, expressions, partitionParents,
				totalN, Double.NaN, allConverged);
	}

	/**
	 * Assembles a {@code MultinomialLogit(...)} expression from a fit: one linear-predictor (eta) string per
	 * non-reference class, continuous parents entering directly and categorical parents as {@code Indicator(...)} terms.
	 */
	private String buildMultinomialLogitExpression(MultinomialLogisticRegression.Result fit, List<Node> continuousParents,
			List<Node> categoricalParents, List<List<String>> categoricalParentStates) {
		List<String> etaExpressions = new ArrayList<>();
		for (int cls = 0; cls < fit.getCoefficients().length; cls++){
			etaExpressions.add(buildEtaExpression(fit.getCoefficients()[cls], continuousParents, categoricalParents, categoricalParentStates));
		}
		return "MultinomialLogit(" + String.join(", ", etaExpressions) + ")";
	}

	/**
	 * The target's unconditional class distribution as a {@code MultinomialLogit} with constant linear predictors
	 * (intercept-only fit over all usable rows), used to fill partitions that have no rows of their own. Falls back to
	 * a uniform distribution (all-zero predictors) if the target has no usable rows at all.
	 */
	private String globalMarginalExpression(Node target, List<String> targetStates) {
		RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
				target.getId(), targetStates, java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList());
		if (selection.getN() == 0){
			List<String> zeros = new ArrayList<>();
			for (int i = 1; i < targetStates.size(); i++){
				zeros.add("0");
			}
			return "MultinomialLogit(" + String.join(", ", zeros) + ")";
		}
		MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);
		return buildMultinomialLogitExpression(fit, java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList());
	}

	private NodeLearningResult skip(Node target, String reason) {
		return new NodeLearningResult(target, true, reason, null, null, null, java.util.Collections.emptyList(), 0, Double.NaN, false);
	}

	/**
	 * Builds one class's linear predictor expression: {@code coefficients[0]} is the intercept,
	 * {@code coefficients[1..continuousParents.size()]} are the continuous-parent slopes (in the same order as
	 * {@code continuousParents}), and the remainder are dummy coefficients for each categorical parent's
	 * non-reference states (in {@code categoricalParents} order), matching
	 * {@link RegressionDataset#selectMixedCategoricalRows}'s column layout exactly.
	 */
	private String buildEtaExpression(double[] coefficients, List<Node> continuousParents, List<Node> categoricalParents, List<List<String>> categoricalParentStates) {

		StringBuilder sb = new StringBuilder(formatNumber(coefficients[0]));
		int j = 1;

		for (Node parent : continuousParents){
			appendTerm(sb, coefficients[j], parent.getId());
			j++;
		}

		for (int p = 0; p < categoricalParents.size(); p++){
			String parentId = categoricalParents.get(p).getId();
			List<String> states = categoricalParentStates.get(p);
			for (int s = 1; s < states.size(); s++){
				appendTerm(sb, coefficients[j], "Indicator(" + parentId + ",\"" + states.get(s) + "\")");
				j++;
			}
		}

		return sb.toString();
	}

	private void appendTerm(StringBuilder sb, double coefficient, String variableTerm) {
		if (coefficient >= 0){
			sb.append(" + ").append(formatNumber(coefficient));
		}
		else {
			sb.append(" - ").append(formatNumber(-coefficient));
		}
		sb.append("*").append(variableTerm);
	}

	private List<String> statesOf(Node node) {
		return node.getStates().stream().map(State::getLabel).collect(Collectors.toList());
	}

	private static String formatNumber(double value) {
		if (value == 0){
			return "0";
		}
		BigDecimal bd = BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
		return bd.toPlainString();
	}
}
