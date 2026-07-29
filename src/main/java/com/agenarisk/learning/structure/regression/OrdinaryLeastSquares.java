package com.agenarisk.learning.structure.regression;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.QRDecomposition;

/**
 * Fits an ordinary least squares linear regression y = b0 + b1*x1 + ... + bk*xk using QR decomposition.
 * <br>
 * This is a plain single-pass fit over whatever complete rows are handed to it; it does not do any row selection,
 * missing value handling, or partitioning - that is the responsibility of the caller.
 *
 * @author Eugene Dementiev
 */
public class OrdinaryLeastSquares {

	/**
	 * Above this ratio of the largest to smallest diagonal magnitude in the QR decomposition's R factor, the design
	 * matrix is treated as too ill-conditioned to trust, even though {@link QRDecomposition#hasFullRank()} (which
	 * only catches *exact* rank deficiency) still reports true. A near-constant regressor - or any near-collinear
	 * combination of columns - passes that exact check yet yields coefficients that blow up to explain noise-level
	 * variance, producing expressions with coefficients in the 1e8-1e13 range; besides being meaningless, calculating
	 * a network with such an expression can make the engine's dynamic discretization fail to converge, hanging
	 * calculation. Treating this the same as exact rank-deficiency reuses the existing, already-tested fallback chain
	 * (pooled ANCOVA, then global mean) that callers already have for that case - no other code needed to change.
	 */
	private static final double MAX_CONDITION_RATIO = 1e8;

	/**
	 * Result of an OLS fit.
	 */
	public static class Result {

		/**
		 * Fitted coefficients, index 0 is the intercept, index i+1 is the coefficient for regressor column i.
		 */
		private final double[] coefficients;

		/**
		 * Number of rows used in the fit.
		 */
		private final int n;

		/**
		 * Number of regressor columns (not counting the intercept).
		 */
		private final int k;

		/**
		 * Whether the design matrix (with intercept column) was full rank.
		 * <br>
		 * If false, the coefficients should not be trusted as-is - caller should fall back to a different fitting
		 * strategy (e.g. pooling across partitions).
		 */
		private final boolean fullRank;

		/**
		 * Coefficient of determination, computed against the fitted rows.
		 */
		private final double r2;

		/**
		 * Unbiased residual variance, i.e. SSE / (n - k - 1). NaN if n &lt;= k + 1 (no residual degrees of freedom).
		 */
		private final double residualVariance;

		private Result(double[] coefficients, int n, int k, boolean fullRank, double r2, double residualVariance) {
			this.coefficients = coefficients;
			this.n = n;
			this.k = k;
			this.fullRank = fullRank;
			this.r2 = r2;
			this.residualVariance = residualVariance;
		}

		public double[] getCoefficients() {
			return coefficients;
		}

		public double getIntercept() {
			return coefficients[0];
		}

		public int getN() {
			return n;
		}

		public int getK() {
			return k;
		}

		public boolean isFullRank() {
			return fullRank;
		}

		public double getR2() {
			return r2;
		}

		public double getResidualVariance() {
			return residualVariance;
		}

		/**
		 * @return degrees of freedom remaining after fitting the intercept and all regressors
		 */
		public int getResidualDegreesOfFreedom() {
			return n - k - 1;
		}
	}

	/**
	 * Fits y = b0 + b1*x1 + ... + bk*xk against the provided rows.
	 *
	 * @param x rows of regressor values, x[row][col]; may have zero columns (intercept-only / mean fit)
	 * @param y observed values, one per row
	 *
	 * @return the fit Result
	 *
	 * @throws IllegalArgumentException if x and y have inconsistent dimensions or there are no rows
	 */
	public static Result fit(double[][] x, double[] y) {

		if (x == null || y == null || x.length != y.length || x.length == 0){
			throw new IllegalArgumentException("x and y must be non-null, non-empty, and of equal length");
		}

		int n = y.length;
		int k = x[0].length;

		for (double[] row : x){
			if (row.length != k){
				throw new IllegalArgumentException("All rows of x must have the same number of columns");
			}
		}

		DoubleMatrix2D design = DoubleFactory2D.dense.make(n, k + 1);
		DoubleMatrix2D target = DoubleFactory2D.dense.make(n, 1);
		for (int row = 0; row < n; row++){
			design.setQuick(row, 0, 1);
			for (int col = 0; col < k; col++){
				design.setQuick(row, col + 1, x[row][col]);
			}
			target.setQuick(row, 0, y[row]);
		}

		QRDecomposition qr = new QRDecomposition(design);
		boolean fullRank = qr.hasFullRank() && isWellConditioned(qr.getR());

		double[] coefficients = new double[k + 1];
		double[] fitted = new double[n];

		if (fullRank){
			DoubleMatrix2D solved = qr.solve(target);
			for (int col = 0; col <= k; col++){
				coefficients[col] = solved.getQuick(col, 0);
			}
			for (int row = 0; row < n; row++){
				double yHat = coefficients[0];
				for (int col = 0; col < k; col++){
					yHat += coefficients[col + 1] * x[row][col];
				}
				fitted[row] = yHat;
			}
		}
		else {
			// Rank-deficient: still report n/k/fullRank=false so the caller can decide to fall back;
			// coefficients are left as zero/unusable, fitted values default to the mean of y for R2 bookkeeping only.
			double meanY = mean(y);
			for (int row = 0; row < n; row++){
				fitted[row] = meanY;
			}
		}

		double meanY = mean(y);
		double sse = 0;
		double sst = 0;
		for (int row = 0; row < n; row++){
			double residual = y[row] - fitted[row];
			sse += residual * residual;
			double dev = y[row] - meanY;
			sst += dev * dev;
		}

		double r2 = (sst > 0) ? (1 - sse / sst) : Double.NaN;

		int residualDf = n - k - 1;
		double residualVariance = (fullRank && residualDf > 0) ? (sse / residualDf) : Double.NaN;

		return new Result(coefficients, n, k, fullRank, r2, residualVariance);
	}

	/**
	 * @param r the R factor from a QR decomposition of the design matrix
	 *
	 * @return false if the ratio of its largest to smallest diagonal magnitude exceeds {@link #MAX_CONDITION_RATIO}
	 * (or either is zero) - a cheap, standard proxy for the design matrix's condition number
	 */
	private static boolean isWellConditioned(DoubleMatrix2D r) {
		int size = Math.min(r.rows(), r.columns());
		double maxAbs = 0;
		double minAbs = Double.POSITIVE_INFINITY;
		for (int i = 0; i < size; i++){
			double d = Math.abs(r.getQuick(i, i));
			if (d > maxAbs){
				maxAbs = d;
			}
			if (d < minAbs){
				minAbs = d;
			}
		}
		if (minAbs == 0 || maxAbs == 0){
			return false;
		}
		return (maxAbs / minAbs) <= MAX_CONDITION_RATIO;
	}

	private static double mean(double[] values) {
		double sum = 0;
		for (double v : values){
			sum += v;
		}
		return sum / values.length;
	}
}
