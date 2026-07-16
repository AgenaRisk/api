package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.regression.OrdinaryLeastSquares;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OrdinaryLeastSquaresTest {

	private static final double DELTA = 1e-9;

	@Test
	public void testExactNoNoiseRecoversCoefficients() {
		// y = 2 + 3*x1 - 1*x2, no noise
		double[][] x = {
			{0, 0},
			{1, 0},
			{0, 1},
			{1, 1},
			{2, 0},
			{0, 2},
			{2, 1},
			{1, 2}
		};
		double[] y = new double[x.length];
		for (int i = 0; i < x.length; i++){
			y[i] = 2 + 3 * x[i][0] - 1 * x[i][1];
		}

		OrdinaryLeastSquares.Result result = OrdinaryLeastSquares.fit(x, y);

		Assertions.assertTrue(result.isFullRank());
		Assertions.assertEquals(2, result.getIntercept(), DELTA);
		Assertions.assertEquals(3, result.getCoefficients()[1], DELTA);
		Assertions.assertEquals(-1, result.getCoefficients()[2], DELTA);
		Assertions.assertEquals(1.0, result.getR2(), DELTA);
		Assertions.assertEquals(0.0, result.getResidualVariance(), 1e-6);
		Assertions.assertEquals(8, result.getN());
		Assertions.assertEquals(2, result.getK());
	}

	@Test
	public void testInterceptOnlyRecoversMean() {
		double[][] x = new double[5][0];
		double[] y = {1, 2, 3, 4, 10};

		OrdinaryLeastSquares.Result result = OrdinaryLeastSquares.fit(x, y);

		double expectedMean = (1 + 2 + 3 + 4 + 10) / 5.0;
		Assertions.assertTrue(result.isFullRank());
		Assertions.assertEquals(expectedMean, result.getIntercept(), DELTA);
		Assertions.assertEquals(0, result.getK());
	}

	@Test
	public void testNoisyDataGivesPartialR2() {
		// y roughly follows x but with noise, R2 should be between 0 and 1 (not exactly 1, not NaN)
		double[][] x = {{1}, {2}, {3}, {4}, {5}, {6}};
		double[] y = {2.1, 3.9, 6.2, 7.8, 10.1, 11.9};

		OrdinaryLeastSquares.Result result = OrdinaryLeastSquares.fit(x, y);

		Assertions.assertTrue(result.isFullRank());
		Assertions.assertTrue(result.getR2() > 0.9 && result.getR2() < 1.0);
		Assertions.assertTrue(result.getResidualVariance() > 0);
		Assertions.assertEquals(4, result.getResidualDegreesOfFreedom());
	}

	@Test
	public void testRankDeficientIsDetected() {
		// x2 is exactly 2*x1, so the design matrix is rank-deficient
		double[][] x = {
			{1, 2},
			{2, 4},
			{3, 6},
			{4, 8}
		};
		double[] y = {1, 2, 3, 4};

		OrdinaryLeastSquares.Result result = OrdinaryLeastSquares.fit(x, y);

		Assertions.assertFalse(result.isFullRank());
	}

	@Test
	public void testInsufficientRowsThrowsOnMismatch() {
		double[][] x = {{1, 2}, {3, 4}};
		double[] y = {1, 2, 3};

		Assertions.assertThrows(IllegalArgumentException.class, () -> OrdinaryLeastSquares.fit(x, y));
	}
}
