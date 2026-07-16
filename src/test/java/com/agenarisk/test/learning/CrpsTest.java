package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.config.PerformanceEvaluationExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CrpsTest {

	private static final double DELTA = 1e-6;

	@Test
	public void testStandardNormalAtMean() {
		// Textbook closed-form value: CRPS(N(0,1), 0) = 2/sqrt(2*pi) - 1/sqrt(pi)
		double expected = 2 / Math.sqrt(2 * Math.PI) - 1 / Math.sqrt(Math.PI);
		Assertions.assertEquals(expected, PerformanceEvaluationExecutor.calculateCrps(0, 1, 0), DELTA);
	}

	@Test
	public void testSymmetric() {
		double crpsPlus = PerformanceEvaluationExecutor.calculateCrps(0, 1, 1);
		double crpsMinus = PerformanceEvaluationExecutor.calculateCrps(0, 1, -1);
		Assertions.assertEquals(crpsPlus, crpsMinus, DELTA);
	}

	@Test
	public void testScalesLinearlyWithStdDev() {
		// CRPS(N(mu,sigma), x) = sigma * CRPS(N(0,1), (x-mu)/sigma)
		double base = PerformanceEvaluationExecutor.calculateCrps(0, 1, 0);
		double scaled = PerformanceEvaluationExecutor.calculateCrps(5, 2, 5);
		Assertions.assertEquals(2 * base, scaled, DELTA);
	}

	@Test
	public void testNearZeroStdDevFallsBackToAbsoluteError() {
		double crps = PerformanceEvaluationExecutor.calculateCrps(1, 1e-12, 3);
		Assertions.assertEquals(2.0, crps, DELTA);
	}

	@Test
	public void testExactMatchWithNearZeroStdDevIsZero() {
		double crps = PerformanceEvaluationExecutor.calculateCrps(4, 1e-12, 4);
		Assertions.assertEquals(0.0, crps, DELTA);
	}

	@Test
	public void testFartherActualGivesHigherCrps() {
		double near = PerformanceEvaluationExecutor.calculateCrps(0, 1, 0.5);
		double far = PerformanceEvaluationExecutor.calculateCrps(0, 1, 3);
		Assertions.assertTrue(far > near);
	}
}
