package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.exception.NodeException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies a {@link ContinuousRegressionLearner.NodeLearningResult} to its target Node, i.e. actually writes the
 * learned expression(s) back into the model.
 *
 * @author Eugene Dementiev
 */
public class RegressionTableWriter {

	private RegressionTableWriter() {
	}

	/**
	 * Writes the learned expressions from {@code result} onto its target Node.
	 * <br>
	 * Does nothing if the result was skipped - callers should check {@link ContinuousRegressionLearner.NodeLearningResult#isSkipped()}
	 * themselves if they need to react to a skip (e.g. to raise an Advisory message).
	 *
	 * @param result the learning outcome to apply
	 *
	 * @throws NodeException if the expressions can't be parsed or set on the Node
	 */
	public static void apply(ContinuousRegressionLearner.NodeLearningResult result) throws NodeException {

		if (result.isSkipped()){
			return;
		}

		List<String> expressions = result.getPartitionResults().stream()
				.map(ContinuousRegressionLearner.PartitionResult::getExpression)
				.collect(Collectors.toList());

		result.getTarget().setTableFunctions(expressions, result.getPartitionParents());
	}
}
