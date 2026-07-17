package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Determines whether a Node can be learned by the OLS table learner, before any fitting is attempted.
 * <br>
 * The one case this currently rules out (Option A from the design discussion) is a categorical target (Boolean,
 * Labelled or DiscreteReal) with at least one continuous parent. The core engine forces such a node into a
 * deterministic "Comparative" expression (if/else branching on the parent's raw numeric value), which cannot
 * represent a learned probability distribution over the child's states, and manual-table cross-tabulation isn't
 * practical either since the continuous parent is dynamically/simulated-discretised at runtime, not fixed bins. This
 * combination is also generally considered a sign of poor model design (a genuinely continuous driver of a
 * genuinely categorical outcome usually wants an explicit discretisation step modelled deliberately, not learned
 * around). Rather than attempt anything clever here, we flag it and skip.
 * <br>
 * This class only decides eligibility; it does not itself log or raise Advisory messages - callers own that so they
 * can integrate it with whatever reporting mechanism is appropriate for the context.
 *
 * @author Eugene Dementiev
 */
public class RegressionEligibility {

	/**
	 * Eligibility decision for a single target Node.
	 */
	public static class Decision {

		private final Node target;
		private final boolean eligible;
		private final String reason;

		private Decision(Node target, boolean eligible, String reason) {
			this.target = target;
			this.eligible = eligible;
			this.reason = reason;
		}

		public Node getTarget() {
			return target;
		}

		public boolean isEligible() {
			return eligible;
		}

		/**
		 * @return human-readable reason the node was skipped, or null if eligible
		 */
		public String getReason() {
			return reason;
		}
	}

	/**
	 * Evaluates whether {@code target} is eligible for OLS table learning.
	 * <br>
	 * Equivalent to {@code evaluate(target, false)} - a categorical target with a continuous parent remains
	 * ineligible under this overload, preserving existing behavior/callers exactly.
	 *
	 * @param target the candidate regression target
	 *
	 * @return the eligibility Decision
	 */
	public static Decision evaluate(Node target) {
		return evaluate(target, false);
	}

	/**
	 * Evaluates whether {@code target} is eligible for regression-based table learning.
	 *
	 * @param target the candidate regression target
	 * @param allowContinuousParentsForCategoricalTarget if true, a categorical target with continuous parent(s) is
	 * treated as eligible - for use by callers that can learn that case via a logistic/multinomial-logit expression
	 * (see {@link LogisticRegressionLearner}) rather than the plain OLS/manual-NPT learners this eligibility check
	 * was originally written for
	 *
	 * @return the eligibility Decision
	 */
	public static Decision evaluate(Node target, boolean allowContinuousParentsForCategoricalTarget) {

		NodeRole targetRole = NodeRole.of(target);

		if (targetRole == NodeRole.CATEGORICAL && !allowContinuousParentsForCategoricalTarget){
			List<Node> continuousParents = target.getParents().stream()
					.filter(parent -> NodeRole.of(parent) == NodeRole.CONTINUOUS)
					.collect(Collectors.toList());

			if (!continuousParents.isEmpty()){
				String parentIds = continuousParents.stream().map(Node::getId).collect(Collectors.joining(", "));
				String reason = "Node '" + target.getId() + "' is categorical (" + target.getType() + ") but has "
						+ "continuous parent(s) [" + parentIds + "]. This combination can't be learned automatically: "
						+ "the engine can only represent it as a deterministic Comparative expression on the parent's "
						+ "raw value, not a learned probability distribution, and it's usually a sign the model would "
						+ "be better served by an explicit discretisation step. Skipping this node - consider "
						+ "remodelling the edge (e.g. an explicit Ranked or discretised intermediate node) or "
						+ "populating its table manually.";
				return new Decision(target, false, reason);
			}
		}

		return new Decision(target, true, null);
	}

	/**
	 * Evaluates eligibility for every node in {@code targets}.
	 *
	 * @param targets candidate regression targets
	 *
	 * @return list of Decisions, one per target, in the same order
	 */
	public static List<Decision> evaluateAll(List<Node> targets) {
		List<Decision> decisions = new ArrayList<>();
		for (Node target : targets){
			decisions.add(evaluate(target));
		}
		return decisions;
	}
}
