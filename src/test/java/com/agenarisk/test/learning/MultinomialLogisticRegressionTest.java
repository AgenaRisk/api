package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.regression.MultinomialLogisticRegression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MultinomialLogisticRegressionTest {

	@Test
	public void testInterceptOnlyRecoversEmpiricalProportionsExactly() {
		// No ridge penalty is ever applied to intercepts, so an intercept-only (k=0) fit should be the exact,
		// unregularized MLE - which for a multinomial with no regressors is just the empirical class proportions.
		double[][] x = new double[10][0];
		int[] y = {0, 0, 0, 0, 0, 0, 1, 1, 2, 2}; // class0: 6/10, class1: 2/10, class2: 2/10

		MultinomialLogisticRegression.Result result = MultinomialLogisticRegression.fit(x, y, 3);

		Assertions.assertTrue(result.isConverged());
		double[] probs = result.predictProbabilities(new double[0]);
		Assertions.assertEquals(0.6, probs[0], 1e-6);
		Assertions.assertEquals(0.2, probs[1], 1e-6);
		Assertions.assertEquals(0.2, probs[2], 1e-6);
	}

	@Test
	public void testBinaryLogisticRecoversStrongSeparationDirectionally() {
		// x < 0 -> always class 0, x > 0 -> always class 1 (perfectly separable)
		double[][] x = {{-3}, {-2}, {-1}, {1}, {2}, {3}};
		int[] y = {0, 0, 0, 1, 1, 1};

		MultinomialLogisticRegression.Result result = MultinomialLogisticRegression.fit(x, y, 2);

		Assertions.assertTrue(result.isConverged());
		// Ridge keeps the slope finite despite perfect separation (unregularized MLE would diverge to +infinity)
		double slope = result.getCoefficients()[0][1];
		Assertions.assertTrue(Double.isFinite(slope));
		Assertions.assertTrue(slope > 0); // correct direction: higher x -> more likely class 1

		double[] probsNegative = result.predictProbabilities(new double[]{-2});
		double[] probsPositive = result.predictProbabilities(new double[]{2});
		Assertions.assertTrue(probsNegative[0] > 0.5); // predicts class 0 for negative x
		Assertions.assertTrue(probsPositive[1] > 0.5); // predicts class 1 for positive x
	}

	@Test
	public void testInformativePredictorGivesHighPseudoR2() {
		double[][] x = {{-3}, {-2}, {-1}, {1}, {2}, {3}, {-2.5}, {2.5}};
		int[] y = {0, 0, 0, 1, 1, 1, 0, 1};

		MultinomialLogisticRegression.Result result = MultinomialLogisticRegression.fit(x, y, 2);

		Assertions.assertTrue(result.getPseudoR2() > 0.3);
		Assertions.assertTrue(result.getPseudoR2() <= 1.0);
	}

	@Test
	public void testUninformativePredictorGivesLowPseudoR2() {
		// x alternates but has no relationship with y at all
		double[][] x = {{1}, {2}, {1}, {2}, {1}, {2}, {1}, {2}};
		int[] y = {0, 1, 1, 0, 0, 1, 1, 0};

		MultinomialLogisticRegression.Result result = MultinomialLogisticRegression.fit(x, y, 2);

		Assertions.assertTrue(result.getPseudoR2() < 0.15);
	}

	@Test
	public void testProbabilitiesSumToOneForThreeClasses() {
		double[][] x = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 0}, {0, 2}};
		int[] y = {0, 1, 2, 1, 0, 2};

		MultinomialLogisticRegression.Result result = MultinomialLogisticRegression.fit(x, y, 3);

		double[] probs = result.predictProbabilities(new double[]{1, 1});
		double sum = 0;
		for (double p : probs){
			Assertions.assertTrue(p >= 0 && p <= 1);
			sum += p;
		}
		Assertions.assertEquals(1.0, sum, 1e-9);
	}

	@Test
	public void testUnobservedDummyStateShrinksTowardsZeroCoefficientNotInfinity() {
		// Two dummy columns: col0 always 0 in training data (its state never observed) - ridge should keep its
		// coefficient near 0 (i.e. "no different from reference") rather than exploding or crashing.
		double[][] x = {{0, 0}, {0, 1}, {0, 0}, {0, 1}, {0, 0}, {0, 1}};
		int[] y = {0, 1, 0, 1, 0, 1};

		MultinomialLogisticRegression.Result result = MultinomialLogisticRegression.fit(x, y, 2);

		Assertions.assertTrue(result.isConverged());
		double col0Coefficient = result.getCoefficients()[0][1];
		Assertions.assertTrue(Double.isFinite(col0Coefficient));
		Assertions.assertEquals(0.0, col0Coefficient, 0.3);
	}
}
