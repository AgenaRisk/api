package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.exception.NodeException;

/**
 * Applies a {@link CategoricalRegressionLearner.NodeLearningResult} to its target Node, i.e. writes the learned
 * probability table back into the model as a manual NPT.
 *
 * @author Eugene Dementiev
 */
public class CategoricalTableWriter {

	private CategoricalTableWriter() {
	}

	/**
	 * Writes the learned NPT from {@code result} onto its target Node.
	 * <br>
	 * Does nothing if the result was skipped - callers should check
	 * {@link CategoricalRegressionLearner.NodeLearningResult#isSkipped()} themselves if they need to react to a skip
	 * (e.g. to raise an Advisory message).
	 *
	 * @param result the learning outcome to apply
	 *
	 * @throws NodeException if the table can't be set on the Node (e.g. its parent order has changed since
	 * {@code result} was computed)
	 */
	public static void apply(CategoricalRegressionLearner.NodeLearningResult result) throws NodeException {

		if (result.isSkipped()){
			return;
		}

		result.getTarget().setTableColumns(result.getNpt());
	}
}
