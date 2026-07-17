package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.learning.structure.regression.MultinomialLogisticRegression;

/**
 * {@link LocalScore} for a categorical target, built directly from a {@link MultinomialLogisticRegression.Result} -
 * its log-likelihood is already in nats, no derivation needed. Covers both the categorical-only-parents case and the
 * mixed continuous+categorical-parents case identically, since both are a single multinomial logit fit (unlike the
 * continuous-target case, a categorical target's parents never need partitioning - the multinomial logit already
 * handles categorical parents via dummy encoding within one fit).
 *
 * @author Eugene Dementiev
 */
public class CategoricalLocalScore implements LocalScore {

	private final MultinomialLogisticRegression.Result fit;

	private CategoricalLocalScore(MultinomialLogisticRegression.Result fit) {
		this.fit = fit;
	}

	/**
	 * @param fit the multinomial logit fit
	 *
	 * @return the CategoricalLocalScore wrapping it
	 */
	public static CategoricalLocalScore from(MultinomialLogisticRegression.Result fit) {
		return new CategoricalLocalScore(fit);
	}

	public MultinomialLogisticRegression.Result getFit() {
		return fit;
	}

	public boolean isConverged() {
		return fit.isConverged();
	}

	@Override
	public double getLogLikelihood() {
		return fit.getLogLikelihood();
	}

	@Override
	public int getFreeParameterCount() {
		int numFreeClasses = fit.getNumClasses() - 1;
		int p = fit.getK() + 1; // regressor columns + intercept
		return numFreeClasses * p;
	}

	@Override
	public int getN() {
		return fit.getN();
	}
}
