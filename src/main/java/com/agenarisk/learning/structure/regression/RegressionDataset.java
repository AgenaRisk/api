package com.agenarisk.learning.structure.regression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uk.co.agena.minerva.util.EM.Data;

/**
 * Wraps a {@link Data} instance (the same CSV/missing-value dataset abstraction the existing EM-based table learner
 * uses) and selects listwise-complete rows for a single node's regression: a row is eligible if the target column and
 * every continuous-parent column used as a regressor are present and numeric, and, when fitting a specific partition
 * combination, if every partition-parent column matches that combination's state exactly.
 * <br>
 * Row eligibility is evaluated independently per node and per partition combination - a row missing an unrelated
 * column elsewhere in the dataset does not exclude it here.
 *
 * @author Eugene Dementiev
 */
public class RegressionDataset {

	/**
	 * Separator used to join categorical parent values into a single lookup key when matching a row against a
	 * known partition combination. A control character keeps it extremely unlikely to collide with real data
	 * values, which is the only property this needs.
	 */
	private static final String KEY_DELIMITER = String.valueOf((char) 1);

	private final Data data;
	private final Map<String, Integer> columnIndex;

	public RegressionDataset(Data data) {
		this.data = data;
		this.columnIndex = new HashMap<>();
		for (int i = 0; i < data.dataVariables.size(); i++){
			columnIndex.put(data.dataVariables.get(i), i);
		}
	}

	public boolean hasColumn(String nodeId) {
		return columnIndex.containsKey(nodeId);
	}

	/**
	 * Result of a row selection: parallel regressor matrix and target vector, ready to hand to
	 * {@link OrdinaryLeastSquares#fit(double[][], double[])}.
	 */
	public static class Selection {

		private final double[][] x;
		private final double[] y;

		private Selection(double[][] x, double[] y) {
			this.x = x;
			this.y = y;
		}

		public double[][] getX() {
			return x;
		}

		public double[] getY() {
			return y;
		}

		public int getN() {
			return y.length;
		}
	}

	/**
	 * Selects listwise-complete rows for fitting {@code targetId} against {@code continuousParentIds}, optionally
	 * restricted to rows matching a specific partition combination.
	 *
	 * @param targetId node id of the regression target; must be present as a data column
	 * @param continuousParentIds ordered node ids of continuous parents to use as regressors; may be empty
	 * (intercept-only fit)
	 * @param partitionCombination if non-null, only rows whose partition-parent columns match this combination
	 * exactly are included; if null, no partition filtering is applied
	 *
	 * @return the selected rows, or an empty Selection (n=0) if the target column is absent from the data or no
	 * rows qualify
	 */
	public Selection selectRows(String targetId, List<String> continuousParentIds, PartitionEnumerator.Combination partitionCombination) {

		Integer targetCol = columnIndex.get(targetId);
		if (targetCol == null){
			return new Selection(new double[0][0], new double[0]);
		}

		int[] parentCols = new int[continuousParentIds.size()];
		for (int i = 0; i < continuousParentIds.size(); i++){
			Integer col = columnIndex.get(continuousParentIds.get(i));
			if (col == null){
				// A required regressor column is entirely absent from the data - no row can qualify
				return new Selection(new double[0][0], new double[0]);
			}
			parentCols[i] = col;
		}

		Map<Integer, String> partitionColsToStates = new HashMap<>();
		if (partitionCombination != null){
			for (Map.Entry<String, String> entry : partitionCombination.getStatesByNodeId().entrySet()){
				Integer col = columnIndex.get(entry.getKey());
				if (col == null){
					// A partition parent column is entirely absent from the data - no row can qualify for this combination
					return new Selection(new double[0][0], new double[0]);
				}
				partitionColsToStates.put(col, entry.getValue());
			}
		}

		List<double[]> xRows = new ArrayList<>();
		List<Double> yValues = new ArrayList<>();

		for (int row = 1; row < data.obsDataArray.length; row++){
			String[] rowData = data.obsDataArray[row];

			String targetRaw = rowData[targetCol];
			if (isMissing(targetRaw)){
				continue;
			}
			Double targetValue = parseDoubleOrNull(targetRaw);
			if (targetValue == null){
				continue;
			}

			double[] xRow = new double[parentCols.length];
			boolean rowOk = true;
			for (int i = 0; i < parentCols.length; i++){
				String raw = rowData[parentCols[i]];
				if (isMissing(raw)){
					rowOk = false;
					break;
				}
				Double value = parseDoubleOrNull(raw);
				if (value == null){
					rowOk = false;
					break;
				}
				xRow[i] = value;
			}
			if (!rowOk){
				continue;
			}

			for (Map.Entry<Integer, String> entry : partitionColsToStates.entrySet()){
				String raw = rowData[entry.getKey()];
				if (isMissing(raw) || !raw.equals(entry.getValue())){
					rowOk = false;
					break;
				}
			}
			if (!rowOk){
				continue;
			}

			xRows.add(xRow);
			yValues.add(targetValue);
		}

		double[][] x = xRows.toArray(new double[0][]);
		double[] y = new double[yValues.size()];
		for (int i = 0; i < y.length; i++){
			y[i] = yValues.get(i);
		}

		return new Selection(x, y);
	}

	/**
	 * Result of a pooled row selection for the ANCOVA fallback fit: continuous-parent columns followed by one dummy
	 * column per non-reference partition combination (combination 0 is the implicit reference, absorbed into the
	 * intercept).
	 */
	public static class PooledSelection {

		private final double[][] x;
		private final double[] y;

		private PooledSelection(double[][] x, double[] y) {
			this.x = x;
			this.y = y;
		}

		public double[][] getX() {
			return x;
		}

		public double[] getY() {
			return y;
		}

		public int getN() {
			return y.length;
		}
	}

	/**
	 * Selects listwise-complete rows across ALL partition combinations for a pooled ANCOVA-style fit: a row qualifies
	 * if the target and continuous parents are present, and every categorical parent column is present and its raw
	 * value matches one of the known combinations' states exactly (rows with an unrecognised categorical value are
	 * skipped, since they can't be encoded as a dummy against the model's declared combinations).
	 *
	 * @param targetId node id of the regression target
	 * @param continuousParentIds ordered node ids of continuous parents to use as regressors
	 * @param categoricalParentIds ordered node ids of categorical parents, matching the order used to build
	 * {@code combinations}
	 * @param combinations all partition combinations for the categorical parents, in the same order used elsewhere
	 * (combination 0 is the reference, absorbed into the intercept)
	 *
	 * @return pooled selection with continuous-parent columns followed by one dummy column per non-reference
	 * combination
	 */
	public PooledSelection selectPooledRows(String targetId, List<String> continuousParentIds, List<String> categoricalParentIds, List<PartitionEnumerator.Combination> combinations) {

		Integer targetCol = columnIndex.get(targetId);
		if (targetCol == null){
			return new PooledSelection(new double[0][0], new double[0]);
		}

		int[] parentCols = new int[continuousParentIds.size()];
		for (int i = 0; i < continuousParentIds.size(); i++){
			Integer col = columnIndex.get(continuousParentIds.get(i));
			if (col == null){
				return new PooledSelection(new double[0][0], new double[0]);
			}
			parentCols[i] = col;
		}

		int[] categoricalCols = new int[categoricalParentIds.size()];
		for (int i = 0; i < categoricalParentIds.size(); i++){
			Integer col = columnIndex.get(categoricalParentIds.get(i));
			if (col == null){
				return new PooledSelection(new double[0][0], new double[0]);
			}
			categoricalCols[i] = col;
		}

		Map<String, Integer> combinationKeyToIndex = new HashMap<>();
		for (int i = 0; i < combinations.size(); i++){
			combinationKeyToIndex.put(combinationKey(combinations.get(i), categoricalParentIds), i);
		}

		int numDummies = Math.max(0, combinations.size() - 1);
		List<double[]> xRows = new ArrayList<>();
		List<Double> yValues = new ArrayList<>();

		for (int row = 1; row < data.obsDataArray.length; row++){
			String[] rowData = data.obsDataArray[row];

			String targetRaw = rowData[targetCol];
			if (isMissing(targetRaw)){
				continue;
			}
			Double targetValue = parseDoubleOrNull(targetRaw);
			if (targetValue == null){
				continue;
			}

			double[] continuousValues = new double[parentCols.length];
			boolean rowOk = true;
			for (int i = 0; i < parentCols.length; i++){
				String raw = rowData[parentCols[i]];
				if (isMissing(raw)){
					rowOk = false;
					break;
				}
				Double value = parseDoubleOrNull(raw);
				if (value == null){
					rowOk = false;
					break;
				}
				continuousValues[i] = value;
			}
			if (!rowOk){
				continue;
			}

			StringBuilder keyBuilder = new StringBuilder();
			for (int i = 0; i < categoricalCols.length; i++){
				String raw = rowData[categoricalCols[i]];
				if (isMissing(raw)){
					rowOk = false;
					break;
				}
				keyBuilder.append(raw).append(KEY_DELIMITER);
			}
			if (!rowOk){
				continue;
			}

			Integer combinationIndex = combinationKeyToIndex.get(keyBuilder.toString());
			if (combinationIndex == null){
				// Row's categorical values don't match any known state combination - skip
				continue;
			}

			double[] xRow = new double[parentCols.length + numDummies];
			System.arraycopy(continuousValues, 0, xRow, 0, continuousValues.length);
			if (combinationIndex > 0){
				xRow[parentCols.length + (combinationIndex - 1)] = 1;
			}

			xRows.add(xRow);
			yValues.add(targetValue);
		}

		double[][] x = xRows.toArray(new double[0][]);
		double[] y = new double[yValues.size()];
		for (int i = 0; i < y.length; i++){
			y[i] = yValues.get(i);
		}

		return new PooledSelection(x, y);
	}

	private String combinationKey(PartitionEnumerator.Combination combination, List<String> categoricalParentIds) {
		StringBuilder keyBuilder = new StringBuilder();
		for (String nodeId : categoricalParentIds){
			keyBuilder.append(combination.getState(nodeId)).append(KEY_DELIMITER);
		}
		return keyBuilder.toString();
	}

	/**
	 * Result of a categorical row selection: main-effects dummy-encoded parent design matrix and the target's class
	 * index per row, ready to hand to {@link MultinomialLogisticRegression#fit(double[][], int[], int)}.
	 */
	public static class CategoricalSelection {

		private final double[][] x;
		private final int[] y;

		private CategoricalSelection(double[][] x, int[] y) {
			this.x = x;
			this.y = y;
		}

		public double[][] getX() {
			return x;
		}

		public int[] getY() {
			return y;
		}

		public int getN() {
			return y.length;
		}
	}

	/**
	 * Selects listwise-complete rows for a categorical target regressed on its categorical parents: a row qualifies
	 * if the target's value is one of {@code targetStates} and every parent's value is one of that parent's known
	 * states - rows with a missing or unrecognised value (for the target or any parent) are skipped.
	 * <br>
	 * Parents are dummy-encoded as main effects only (no interaction terms): each parent with {@code S} states
	 * contributes {@code S - 1} dummy columns (state index 0 is the implicit reference), concatenated in
	 * {@code parentIds} order.
	 *
	 * @param targetId node id of the categorical regression target
	 * @param targetStates the target's states in index order (index 0 first); row values are matched against these
	 * exactly to produce the class index
	 * @param parentIds ordered node ids of categorical parents to dummy-encode; may be empty (intercept-only fit)
	 * @param parentStates parent states in index order, aligned with {@code parentIds} (state index 0 per parent is
	 * the reference, dropped from the design matrix)
	 *
	 * @return the selected rows, or an empty Selection (n=0) if the target column is absent or no rows qualify
	 */
	public CategoricalSelection selectCategoricalRows(String targetId, List<String> targetStates, List<String> parentIds, List<List<String>> parentStates) {

		Integer targetCol = columnIndex.get(targetId);
		if (targetCol == null){
			return new CategoricalSelection(new double[0][0], new int[0]);
		}

		Map<String, Integer> targetStateIndex = new HashMap<>();
		for (int i = 0; i < targetStates.size(); i++){
			targetStateIndex.put(targetStates.get(i), i);
		}

		int[] parentCols = new int[parentIds.size()];
		List<Map<String, Integer>> parentStateIndexes = new ArrayList<>();
		int[] dummyOffsets = new int[parentIds.size()];
		int totalDummyColumns = 0;
		for (int i = 0; i < parentIds.size(); i++){
			Integer col = columnIndex.get(parentIds.get(i));
			if (col == null){
				return new CategoricalSelection(new double[0][0], new int[0]);
			}
			parentCols[i] = col;

			List<String> states = parentStates.get(i);
			Map<String, Integer> stateIndex = new HashMap<>();
			for (int s = 0; s < states.size(); s++){
				stateIndex.put(states.get(s), s);
			}
			parentStateIndexes.add(stateIndex);

			dummyOffsets[i] = totalDummyColumns;
			totalDummyColumns += Math.max(0, states.size() - 1);
		}

		List<double[]> xRows = new ArrayList<>();
		List<Integer> yValues = new ArrayList<>();

		for (int row = 1; row < data.obsDataArray.length; row++){
			String[] rowData = data.obsDataArray[row];

			String targetRaw = rowData[targetCol];
			if (isMissing(targetRaw)){
				continue;
			}
			Integer targetIndex = targetStateIndex.get(targetRaw);
			if (targetIndex == null){
				continue;
			}

			double[] xRow = new double[totalDummyColumns];
			boolean rowOk = true;
			for (int i = 0; i < parentCols.length; i++){
				String raw = rowData[parentCols[i]];
				if (isMissing(raw)){
					rowOk = false;
					break;
				}
				Integer stateIndex = parentStateIndexes.get(i).get(raw);
				if (stateIndex == null){
					rowOk = false;
					break;
				}
				if (stateIndex > 0){
					xRow[dummyOffsets[i] + stateIndex - 1] = 1;
				}
			}
			if (!rowOk){
				continue;
			}

			xRows.add(xRow);
			yValues.add(targetIndex);
		}

		double[][] x = xRows.toArray(new double[0][]);
		int[] y = new int[yValues.size()];
		for (int i = 0; i < y.length; i++){
			y[i] = yValues.get(i);
		}

		return new CategoricalSelection(x, y);
	}

	private boolean isMissing(String raw) {
		return raw == null || raw.isEmpty() || raw.equals(data.missingType);
	}

	private Double parseDoubleOrNull(String raw) {
		try {
			return Double.parseDouble(raw);
		}
		catch (NumberFormatException ex){
			return null;
		}
	}
}
