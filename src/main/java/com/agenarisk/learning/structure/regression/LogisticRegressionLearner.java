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
		private final String expression;
		private final int n;
		private final double pseudoR2;
		private final boolean converged;

		private NodeLearningResult(Node target, boolean skipped, String skipReason, List<Node> continuousParents, List<Node> categoricalParents,
				String expression, int n, double pseudoR2, boolean converged) {
			this.target = target;
			this.skipped = skipped;
			this.skipReason = skipReason;
			this.continuousParents = continuousParents;
			this.categoricalParents = categoricalParents;
			this.expression = expression;
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
		 * @return the single {@code MultinomialLogit(...)} expression to apply via {@link Node#setTableFunction(String)}
		 */
		public String getExpression() {
			return expression;
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

		if (continuousParents.isEmpty()){
			return skip(target, "Node '" + target.getId() + "' has no continuous parents; use CategoricalRegressionLearner instead");
		}

		List<String> targetStates = statesOf(target);
		List<String> continuousParentIds = continuousParents.stream().map(Node::getId).collect(Collectors.toList());
		List<String> categoricalParentIds = categoricalParents.stream().map(Node::getId).collect(Collectors.toList());
		List<List<String>> categoricalParentStates = categoricalParents.stream().map(this::statesOf).collect(Collectors.toList());

		RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
				target.getId(), targetStates, continuousParentIds, categoricalParentIds, categoricalParentStates);

		if (selection.getN() == 0){
			return skip(target, "No usable data found for node '" + target.getId() + "' (and/or its parents); leaving its table unchanged");
		}

		MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);

		List<String> etaExpressions = new ArrayList<>();
		for (int cls = 0; cls < fit.getCoefficients().length; cls++){
			etaExpressions.add(buildEtaExpression(fit.getCoefficients()[cls], continuousParents, categoricalParents, categoricalParentStates));
		}
		String expression = "MultinomialLogit(" + String.join(", ", etaExpressions) + ")";

		return new NodeLearningResult(target, false, null, continuousParents, categoricalParents, expression,
				selection.getN(), fit.getPseudoR2(), fit.isConverged());
	}

	private NodeLearningResult skip(Node target, String reason) {
		return new NodeLearningResult(target, true, reason, null, null, null, 0, Double.NaN, false);
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
