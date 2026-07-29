package com.agenarisk.learning.structure.regression;

import java.util.List;

/**
 * Shared main-effects dummy-encoding convention used by both {@link RegressionDataset#selectCategoricalRows} (row
 * selection, keyed by raw CSV values) and {@link CategoricalRegressionLearner} (evaluating the fitted model at
 * every parent-state combination, keyed by already-resolved state values): for a parent with states
 * {@code [s0, s1, ..., sK-1]}, state index 0 is the implicit reference (all dummies 0), and states 1..K-1 each get
 * their own dummy column, concatenated across parents in order.
 * <br>
 * This is only used for the small number of one-off encodings needed to build the final table (one per parent-state
 * combination); the hot per-row path in {@code RegressionDataset} implements the same convention directly with
 * HashMap lookups for performance rather than calling this.
 *
 * @author Eugene Dementiev
 */
public class CategoricalDummyEncoder {

	private CategoricalDummyEncoder() {
	}

	/**
	 * @param parentStates each parent's states in index order
	 *
	 * @return total number of dummy columns across all parents
	 */
	public static int countDummyColumns(List<List<String>> parentStates) {
		int total = 0;
		for (List<String> states : parentStates){
			total += Math.max(0, states.size() - 1);
		}
		return total;
	}

	/**
	 * @param valuesByParent one resolved state value per parent, aligned with {@code parentStates}
	 * @param parentStates each parent's states in index order
	 *
	 * @return the dummy-encoded row
	 */
	public static double[] encode(List<String> valuesByParent, List<List<String>> parentStates) {
		double[] row = new double[countDummyColumns(parentStates)];
		int offset = 0;
		for (int i = 0; i < parentStates.size(); i++){
			List<String> states = parentStates.get(i);
			int stateIndex = states.indexOf(valuesByParent.get(i));
			if (stateIndex > 0){
				row[offset + stateIndex - 1] = 1;
			}
			offset += Math.max(0, states.size() - 1);
		}
		return row;
	}
}
