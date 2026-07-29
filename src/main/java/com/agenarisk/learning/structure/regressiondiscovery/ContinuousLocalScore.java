package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.learning.structure.regression.OrdinaryLeastSquares;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link LocalScore} for a continuous target, built from one or more {@link OrdinaryLeastSquares.Result} fits (more
 * than one when the target has categorical candidate parents, which are handled by partitioning - one independent
 * OLS fit per parent-state combination, rather than dummy-encoding them into a single fit - summing log-likelihood
 * and free-parameter counts across partitions, since a decomposable score just needs SOME consistent accounting, and
 * per-partition fitting is what {@code ContinuousRegressionLearner} already does for the final persisted table).
 * <br>
 * Recovers the Gaussian MLE log-likelihood from {@link OrdinaryLeastSquares.Result#getResidualVariance()} (which is
 * the *unbiased* estimate, {@code SSE/(n-k-1)}) by first recovering {@code SSE = residualVariance * residualDf}, then
 * using the MLE variance {@code SSE/n} in the standard closed-form Gaussian log-likelihood.
 *
 * @author Eugene Dementiev
 */
public class ContinuousLocalScore implements LocalScore {

	private final List<OrdinaryLeastSquares.Result> partitionFits;
	private final double logLikelihood;
	private final int freeParameterCount;
	private final int n;

	private ContinuousLocalScore(List<OrdinaryLeastSquares.Result> partitionFits, double logLikelihood, int freeParameterCount, int n) {
		this.partitionFits = partitionFits;
		this.logLikelihood = logLikelihood;
		this.freeParameterCount = freeParameterCount;
		this.n = n;
	}

	/**
	 * @param fit a single partition's OLS fit (per-partition Gaussian log-likelihood plus 1 free parameter for the
	 * residual variance, in addition to the intercept+slopes {@code fit} already accounts for via {@code getK()})
	 *
	 * @return the per-partition contribution, or null if the fit is infeasible (rank-deficient or no residual
	 * degrees of freedom) - candidate parent sets that produce an infeasible fit should be treated as illegal moves
	 * during search, not silently downgraded to a fallback fit
	 */
	/**
	 * Floor applied to the recovered MLE variance so a perfect/near-perfect fit (residual variance at or near 0, e.g.
	 * an exact deterministic relationship in the data) doesn't get rejected as "infeasible" or blow the
	 * log-likelihood up to +infinity - it's still the best-fitting model, it should just be a very large (not
	 * infinite) log-likelihood.
	 */
	private static final double MIN_VARIANCE_FLOOR = 1e-9;

	static PartitionContribution scorePartition(OrdinaryLeastSquares.Result fit) {
		int residualDf = fit.getResidualDegreesOfFreedom();
		if (!fit.isFullRank() || residualDf <= 0 || Double.isNaN(fit.getResidualVariance())){
			return null;
		}
		double sse = fit.getResidualVariance() * residualDf;
		int n = fit.getN();
		double mleVariance = Math.max(sse / n, MIN_VARIANCE_FLOOR);
		if (Double.isNaN(mleVariance)){
			return null;
		}
		double ll = -n / 2.0 * (Math.log(2 * Math.PI) + Math.log(mleVariance) + 1);
		int k = 1 + fit.getK() + 1; // intercept + slopes + residual variance
		return new PartitionContribution(ll, k, n);
	}

	static class PartitionContribution {

		final double logLikelihood;
		final int freeParameterCount;
		final int n;

		PartitionContribution(double logLikelihood, int freeParameterCount, int n) {
			this.logLikelihood = logLikelihood;
			this.freeParameterCount = freeParameterCount;
			this.n = n;
		}
	}

	/**
	 * Combines a list of per-partition OLS fits into one whole-node local score. Returns null (candidate parent set
	 * is not scoreable / should be treated as an illegal move) if any partition's fit is infeasible.
	 *
	 * @param fits one OLS fit per parent-state combination (or a single-element list if the target has no
	 * categorical candidate parents)
	 *
	 * @return the combined ContinuousLocalScore, or null if any partition is infeasible
	 */
	public static ContinuousLocalScore combine(List<OrdinaryLeastSquares.Result> fits) {
		List<OrdinaryLeastSquares.Result> retained = new ArrayList<>(fits);
		double totalLogLikelihood = 0;
		int totalFreeParameters = 0;
		int totalN = 0;
		for (OrdinaryLeastSquares.Result fit : fits){
			PartitionContribution contribution = scorePartition(fit);
			if (contribution == null){
				return null;
			}
			totalLogLikelihood += contribution.logLikelihood;
			totalFreeParameters += contribution.freeParameterCount;
			totalN += contribution.n;
		}
		return new ContinuousLocalScore(retained, totalLogLikelihood, totalFreeParameters, totalN);
	}

	public List<OrdinaryLeastSquares.Result> getPartitionFits() {
		return partitionFits;
	}

	@Override
	public double getLogLikelihood() {
		return logLikelihood;
	}

	@Override
	public int getFreeParameterCount() {
		return freeParameterCount;
	}

	@Override
	public int getN() {
		return n;
	}
}
