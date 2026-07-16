package com.agenarisk.learning.structure.regression;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

/**
 * Fits a ridge-regularized multinomial logistic regression (softmax regression) via Newton-Raphson: models
 * P(y=k | x) for a K-state categorical target as a function of regressor columns x, with class 0 as the reference
 * category (its linear predictor fixed at 0).
 * <br>
 * This is the categorical analog of {@link OrdinaryLeastSquares}: for a categorical target whose parents are all
 * themselves categorical, using a main-effects-only (no interaction terms) multinomial logit over the parents'
 * dummy-encoded states shares statistical strength across parent-state combinations, rather than estimating each
 * combination's distribution independently from only the rows that happen to match it exactly - which is what makes
 * it more reliable under sparse/missing data than plain per-combination frequency counting.
 * <br>
 * A small ridge penalty (default) is applied to all non-intercept coefficients. Its purpose is not to bias
 * well-supported estimates, but to keep the fit well-posed when a dummy column is all-zero or the data is otherwise
 * separated - both routine occurrences with sparse categorical predictors, where unregularized maximum likelihood
 * would otherwise diverge to +/-infinity.
 *
 * @author Eugene Dementiev
 */
public class MultinomialLogisticRegression {

	/**
	 * Default ridge penalty, equivalent to a Gaussian(0, 2) prior on each non-intercept coefficient - modest
	 * shrinkage, primarily there to guarantee a well-posed fit under separation rather than to materially bias
	 * well-estimated coefficients.
	 */
	public static final double DEFAULT_RIDGE_LAMBDA = 0.5;

	private static final int MAX_ITERATIONS = 100;
	private static final int MAX_STEP_HALVINGS = 10;
	private static final double CONVERGENCE_TOLERANCE = 1e-8;

	/**
	 * Result of a multinomial logistic regression fit.
	 */
	public static class Result {

		private final double[][] coefficients;
		private final int n;
		private final int k;
		private final int numClasses;
		private final double logLikelihood;
		private final double nullLogLikelihood;
		private final boolean converged;

		private Result(double[][] coefficients, int n, int k, int numClasses, double logLikelihood, double nullLogLikelihood, boolean converged) {
			this.coefficients = coefficients;
			this.n = n;
			this.k = k;
			this.numClasses = numClasses;
			this.logLikelihood = logLikelihood;
			this.nullLogLikelihood = nullLogLikelihood;
			this.converged = converged;
		}

		/**
		 * @return coefficients[k][j] for reference-relative class k (0-indexed, representing target class k+1) and
		 * regressor j (j=0 is the intercept, j=1..getK() are the regressor columns); empty array if numClasses == 1
		 */
		public double[][] getCoefficients() {
			return coefficients;
		}

		public int getN() {
			return n;
		}

		/**
		 * @return number of regressor columns (not counting the intercept)
		 */
		public int getK() {
			return k;
		}

		public int getNumClasses() {
			return numClasses;
		}

		public double getLogLikelihood() {
			return logLikelihood;
		}

		public double getNullLogLikelihood() {
			return nullLogLikelihood;
		}

		public boolean isConverged() {
			return converged;
		}

		/**
		 * @return McFadden's pseudo-R2 = 1 - logLikelihood / nullLogLikelihood; NaN if nullLogLikelihood is 0 (e.g.
		 * a single-row / degenerate fit)
		 */
		public double getPseudoR2() {
			if (nullLogLikelihood == 0){
				return Double.NaN;
			}
			return 1 - (logLikelihood / nullLogLikelihood);
		}

		/**
		 * Predicts the full probability vector over all {@code numClasses} target classes for a given regressor
		 * row, class 0 first.
		 *
		 * @param x regressor values, length must equal {@link #getK()}
		 *
		 * @return probability vector, length numClasses, summing to 1
		 */
		public double[] predictProbabilities(double[] x) {
			double[] eta = new double[numClasses];
			for (int cls = 1; cls < numClasses; cls++){
				double value = coefficients[cls - 1][0];
				for (int j = 0; j < x.length; j++){
					value += coefficients[cls - 1][j + 1] * x[j];
				}
				eta[cls] = value;
			}
			return softmax(eta);
		}
	}

	private MultinomialLogisticRegression() {
	}

	/**
	 * Fits with the default ridge penalty.
	 *
	 * @param x regressor rows, x[row][col]; may have zero columns (intercept-only fit)
	 * @param y class index per row, in [0, numClasses)
	 * @param numClasses number of target classes; class 0 is the reference category
	 *
	 * @return the fit Result
	 */
	public static Result fit(double[][] x, int[] y, int numClasses) {
		return fit(x, y, numClasses, DEFAULT_RIDGE_LAMBDA);
	}

	/**
	 * Fits P(y=k|x) via ridge-regularized multinomial logistic regression.
	 *
	 * @param x regressor rows, x[row][col]; may have zero columns (intercept-only fit)
	 * @param y class index per row, in [0, numClasses)
	 * @param numClasses number of target classes; class 0 is the reference category
	 * @param ridgeLambda ridge penalty applied to non-intercept coefficients; 0 disables regularization (not
	 * recommended for sparse dummy-encoded regressors, see class docs)
	 *
	 * @return the fit Result
	 *
	 * @throws IllegalArgumentException if inputs are inconsistent, empty, or numClasses &lt; 2
	 */
	public static Result fit(double[][] x, int[] y, int numClasses, double ridgeLambda) {

		if (x == null || y == null || x.length != y.length || x.length == 0){
			throw new IllegalArgumentException("x and y must be non-null, non-empty, and of equal length");
		}
		if (numClasses < 2){
			throw new IllegalArgumentException("numClasses must be at least 2");
		}

		int n = y.length;
		int k = x[0].length;
		for (double[] row : x){
			if (row.length != k){
				throw new IllegalArgumentException("All rows of x must have the same number of columns");
			}
		}
		for (int cls : y){
			if (cls < 0 || cls >= numClasses){
				throw new IllegalArgumentException("Class index " + cls + " out of range [0, " + numClasses + ")");
			}
		}

		double nullLogLikelihood = (k == 0) ? 0 : fitInternal(new double[n][0], y, numClasses, ridgeLambda).logLikelihood;

		FitOutcome outcome = fitInternal(x, y, numClasses, ridgeLambda);

		return new Result(outcome.coefficients, n, k, numClasses, outcome.logLikelihood, (k == 0) ? outcome.logLikelihood : nullLogLikelihood, outcome.converged);
	}

	private static class FitOutcome {

		double[][] coefficients;
		double logLikelihood;
		boolean converged;
	}

	private static FitOutcome fitInternal(double[][] x, int[] y, int numClasses, double ridgeLambda) {

		int n = y.length;
		int k = x[0].length;
		int p = k + 1; // regressors per class, including intercept
		int numFreeClasses = numClasses - 1;
		int m = numFreeClasses * p; // total free parameters

		double[] theta = new double[m];
		double prevLogLikelihood = logLikelihoodAt(theta, x, y, numClasses, ridgeLambda, n, k, p, numFreeClasses);
		boolean converged = false;

		for (int iter = 0; iter < MAX_ITERATIONS; iter++){

			double[] gradient = new double[m];
			DoubleMatrix2D information = DoubleFactory2D.dense.make(m, m);

			double[][] probabilities = new double[n][numClasses];
			for (int i = 0; i < n; i++){
				probabilities[i] = predict(theta, x[i], numClasses, k, p, numFreeClasses);
			}

			// Gradient
			for (int cls = 0; cls < numFreeClasses; cls++){
				for (int j = 0; j < p; j++){
					double g = 0;
					for (int i = 0; i < n; i++){
						double xij = (j == 0) ? 1.0 : x[i][j - 1];
						double indicator = (y[i] == cls + 1) ? 1.0 : 0.0;
						g += xij * (indicator - probabilities[i][cls + 1]);
					}
					if (j > 0){
						g -= ridgeLambda * theta[cls * p + j];
					}
					gradient[cls * p + j] = g;
				}
			}

			// Information matrix (negative Hessian)
			for (int cls1 = 0; cls1 < numFreeClasses; cls1++){
				for (int j1 = 0; j1 < p; j1++){
					int row = cls1 * p + j1;
					for (int cls2 = cls1; cls2 < numFreeClasses; cls2++){
						int jStart = (cls2 == cls1) ? j1 : 0;
						for (int j2 = jStart; j2 < p; j2++){
							int col = cls2 * p + j2;
							double h = 0;
							for (int i = 0; i < n; i++){
								double xij1 = (j1 == 0) ? 1.0 : x[i][j1 - 1];
								double xij2 = (j2 == 0) ? 1.0 : x[i][j2 - 1];
								double delta = (cls1 == cls2) ? 1.0 : 0.0;
								h += xij1 * xij2 * probabilities[i][cls1 + 1] * (delta - probabilities[i][cls2 + 1]);
							}
							if (cls1 == cls2 && j1 == j2 && j1 > 0){
								h += ridgeLambda;
							}
							information.setQuick(row, col, h);
							information.setQuick(col, row, h);
						}
					}
				}
			}

			double[] delta;
			try {
				DoubleMatrix2D gradientMatrix = DoubleFactory2D.dense.make(m, 1);
				for (int i = 0; i < m; i++){
					gradientMatrix.setQuick(i, 0, gradient[i]);
				}
				DoubleMatrix2D deltaMatrix = Algebra.DEFAULT.solve(information, gradientMatrix);
				delta = new double[m];
				for (int i = 0; i < m; i++){
					delta[i] = deltaMatrix.getQuick(i, 0);
				}
			}
			catch (Exception ex){
				// Singular information matrix despite ridge - shouldn't normally happen with ridgeLambda > 0, but
				// bail out gracefully with whatever we have rather than propagating a matrix algebra exception
				break;
			}

			double stepScale = 1.0;
			double[] candidateTheta = null;
			double candidateLogLikelihood = Double.NEGATIVE_INFINITY;
			for (int halving = 0; halving <= MAX_STEP_HALVINGS; halving++){
				double[] trial = new double[m];
				for (int i = 0; i < m; i++){
					trial[i] = theta[i] + stepScale * delta[i];
				}
				double trialLogLikelihood = logLikelihoodAt(trial, x, y, numClasses, ridgeLambda, n, k, p, numFreeClasses);
				if (trialLogLikelihood >= prevLogLikelihood || halving == MAX_STEP_HALVINGS){
					candidateTheta = trial;
					candidateLogLikelihood = trialLogLikelihood;
					break;
				}
				stepScale /= 2;
			}

			theta = candidateTheta;

			if (Math.abs(candidateLogLikelihood - prevLogLikelihood) < CONVERGENCE_TOLERANCE * (Math.abs(prevLogLikelihood) + 1)){
				prevLogLikelihood = candidateLogLikelihood;
				converged = true;
				break;
			}
			prevLogLikelihood = candidateLogLikelihood;
		}

		FitOutcome outcome = new FitOutcome();
		outcome.coefficients = toCoefficientMatrix(theta, numFreeClasses, p);
		outcome.logLikelihood = prevLogLikelihood;
		outcome.converged = converged;
		return outcome;
	}

	private static double[][] toCoefficientMatrix(double[] theta, int numFreeClasses, int p) {
		double[][] coefficients = new double[numFreeClasses][p];
		for (int cls = 0; cls < numFreeClasses; cls++){
			System.arraycopy(theta, cls * p, coefficients[cls], 0, p);
		}
		return coefficients;
	}

	private static double[] predict(double[] theta, double[] xRow, int numClasses, int k, int p, int numFreeClasses) {
		double[] eta = new double[numClasses];
		for (int cls = 0; cls < numFreeClasses; cls++){
			double value = theta[cls * p];
			for (int j = 0; j < k; j++){
				value += theta[cls * p + j + 1] * xRow[j];
			}
			eta[cls + 1] = value;
		}
		return softmax(eta);
	}

	private static double[] softmax(double[] eta) {
		double max = eta[0];
		for (double v : eta){
			if (v > max){
				max = v;
			}
		}
		double sum = 0;
		double[] result = new double[eta.length];
		for (int i = 0; i < eta.length; i++){
			result[i] = Math.exp(eta[i] - max);
			sum += result[i];
		}
		for (int i = 0; i < result.length; i++){
			result[i] /= sum;
		}
		return result;
	}

	private static double logLikelihoodAt(double[] theta, double[][] x, int[] y, int numClasses, double ridgeLambda, int n, int k, int p, int numFreeClasses) {
		double logLikelihood = 0;
		for (int i = 0; i < n; i++){
			double[] probabilities = predict(theta, x[i], numClasses, k, p, numFreeClasses);
			double prob = probabilities[y[i]];
			logLikelihood += Math.log(Math.max(prob, 1e-300));
		}
		double penalty = 0;
		for (int cls = 0; cls < numFreeClasses; cls++){
			for (int j = 1; j < p; j++){
				penalty += theta[cls * p + j] * theta[cls * p + j];
			}
		}
		return logLikelihood - 0.5 * ridgeLambda * penalty;
	}
}
