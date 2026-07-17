package com.agenarisk.learning.structure.regressiondiscovery;

/**
 * A node's local fit quality for one candidate parent set, in a form that's summable across the whole DAG (BIC
 * decomposability is what makes greedy local search over individual parent sets valid for optimizing a whole-network
 * score).
 * <br>
 * Convention: natural-log (nats) log-likelihood throughout, and a "higher is better" BIC
 * ({@code BIC = 2*logLikelihood - freeParameterCount*ln(n)} - a maximization-oriented scaling of the textbook BIC,
 * matching the direction greedy hill-climbing search already searches in). This is NOT numerically comparable to the
 * legacy discrete engine's BIC (computed over pre-discretized categorical data on a different scale) - comparing a
 * regression-discovery run against a legacy-discrete run means comparing their own reported numbers side by side,
 * not literally the same score.
 *
 * @author Eugene Dementiev
 */
public interface LocalScore {

	/**
	 * @return natural-log likelihood of the data under this node's fitted local model
	 */
	double getLogLikelihood();

	/**
	 * @return number of free parameters in this node's local model (used for the BIC penalty term)
	 */
	int getFreeParameterCount();

	/**
	 * @return number of data rows actually used in this node's fit
	 */
	int getN();

	/**
	 * @return the maximization-oriented BIC score, {@code 2*logLikelihood - freeParameterCount*ln(n)}
	 */
	default double getBic() {
		return 2 * getLogLikelihood() - getFreeParameterCount() * Math.log(getN());
	}
}
