package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.exception.NodeException;

/**
 * Applies a {@link LogisticRegressionLearner.NodeLearningResult} to its target Node, i.e. writes the learned
 * {@code MultinomialLogit(...)} expression back into the model as a single, non-partitioned table function.
 * <br>
 * Unlike {@link CategoricalTableWriter} (which bakes a manual NPT), this persists a live expression - categorical
 * parent effects are embedded via {@code Indicator(...)} terms inside the expression itself, so partitioning by
 * those parents would be redundant with (and conflict with) the expression.
 *
 * @author Eugene Dementiev
 */
public class LogisticExpressionTableWriter {

	private LogisticExpressionTableWriter() {
	}

	/**
	 * Writes the learned expression from {@code result} onto its target Node.
	 * <br>
	 * Does nothing if the result was skipped - callers should check {@link LogisticRegressionLearner.NodeLearningResult#isSkipped()}
	 * themselves if they need to react to a skip (e.g. to raise an Advisory message).
	 *
	 * @param result the learning outcome to apply
	 *
	 * @throws NodeException if the expression can't be parsed or set on the Node
	 */
	public static void apply(LogisticRegressionLearner.NodeLearningResult result) throws NodeException {

		if (result.isSkipped()){
			return;
		}

		if (result.getPartitionParents().isEmpty()){
			// Single, non-partitioned expression - categorical parent effects are embedded as Indicator(...) terms.
			result.getTarget().setTableFunction(result.getExpression());
		}
		else {
			// forbidIndicatorEncoding: one MultinomialLogit expression per state combination of the partitioned parents.
			result.getTarget().setTableFunctions(result.getExpressions(), result.getPartitionParents());
		}
	}
}
