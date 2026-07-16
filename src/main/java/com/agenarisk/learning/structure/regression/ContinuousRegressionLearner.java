package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Node;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;

/**
 * Learns a continuous target node's table from data via OLS, one regression per combination of its categorical
 * parents' states (no indicator/dummy variables needed for that split - partitioning already gives each combination
 * its own intercept and slopes), regressing on the node's continuous parents within each partition.
 * <br>
 * Row selection is listwise-complete and independent per partition: a row only needs the target and the continuous
 * parents actually being regressed on to be present, plus (when filtering to a specific partition) the categorical
 * parents to match that combination - other columns being missing elsewhere in the dataset doesn't exclude a row
 * here.
 * <br>
 * When a partition doesn't have enough complete rows to support its own fit, this falls back to a pooled
 * ANCOVA-style fit: one shared slope vector across all partitions, with per-partition intercept shifts via dummy
 * variables. If even that is infeasible (too few rows overall), it falls back further to a global mean.
 *
 * @author Eugene Dementiev
 */
public class ContinuousRegressionLearner {

	/**
	 * Controls whether learned expressions are emitted as {@code Normal(mean, variance)} (retaining residual
	 * uncertainty) or a deterministic {@code Arithmetic(mean)}. R2/N/residual variance are computed and reported
	 * either way, regardless of this setting.
	 * <br>
	 * Ignored for {@code Ranked} targets: the core engine only accepts {@code TNormal} expressions on a Ranked
	 * node ({@code RankedEN.supportedFunctionTypes = {TNormal}} - neither {@code Normal} nor {@code Arithmetic} is
	 * valid there, even though nothing in the API layer rejects writing one). Ranked targets always get
	 * {@code TNormal(mean, variance, lowerBound, upperBound)} regardless of this setting.
	 */
	public enum ResidualMode {
		NORMAL,
		ARITHMETIC
	}

	/**
	 * How a particular partition's expression ended up being fitted.
	 */
	public enum FitSource {
		/**
		 * Fitted directly from that partition's own complete-case rows.
		 */
		PARTITION_SPECIFIC,
		/**
		 * That partition didn't have enough complete rows on its own; fell back to a pooled fit sharing slopes
		 * across all partitions, with a per-partition intercept shift.
		 */
		POOLED_ANCOVA,
		/**
		 * Even the pooled fit was infeasible; fell back to the plain mean of the target over all available rows,
		 * ignoring parents entirely.
		 */
		GLOBAL_MEAN
	}

	/**
	 * Learning outcome for one partition (or the whole node, when it has no categorical parents to partition by).
	 */
	public static class PartitionResult {

		private final PartitionEnumerator.Combination combination;
		private final String expression;
		private final int n;
		private final double r2;
		private final double residualVariance;
		private final FitSource fitSource;

		private PartitionResult(PartitionEnumerator.Combination combination, String expression, int n, double r2, double residualVariance, FitSource fitSource) {
			this.combination = combination;
			this.expression = expression;
			this.n = n;
			this.r2 = r2;
			this.residualVariance = residualVariance;
			this.fitSource = fitSource;
		}

		/**
		 * @return the partition's parent-state combination, or null if the node has no categorical parents
		 */
		public PartitionEnumerator.Combination getCombination() {
			return combination;
		}

		public String getExpression() {
			return expression;
		}

		public int getN() {
			return n;
		}

		public double getR2() {
			return r2;
		}

		public double getResidualVariance() {
			return residualVariance;
		}

		public FitSource getFitSource() {
			return fitSource;
		}
	}

	/**
	 * Full learning outcome for one target node.
	 */
	public static class NodeLearningResult {

		private final Node target;
		private final boolean skipped;
		private final String skipReason;
		private final List<Node> partitionParents;
		private final List<PartitionResult> partitionResults;

		private NodeLearningResult(Node target, boolean skipped, String skipReason, List<Node> partitionParents, List<PartitionResult> partitionResults) {
			this.target = target;
			this.skipped = skipped;
			this.skipReason = skipReason;
			this.partitionParents = partitionParents;
			this.partitionResults = partitionResults;
		}

		public Node getTarget() {
			return target;
		}

		public boolean isSkipped() {
			return skipped;
		}

		public String getSkipReason() {
			return skipReason;
		}

		public List<Node> getPartitionParents() {
			return partitionParents;
		}

		public List<PartitionResult> getPartitionResults() {
			return partitionResults;
		}
	}

	private static final int ABSOLUTE_MIN_ROWS = 5;

	private final RegressionDataset dataset;
	private final ResidualMode residualMode;
	private final int minRowsPerPartition;

	public ContinuousRegressionLearner(RegressionDataset dataset, ResidualMode residualMode) {
		this(dataset, residualMode, ABSOLUTE_MIN_ROWS);
	}

	public ContinuousRegressionLearner(RegressionDataset dataset, ResidualMode residualMode, int minRowsPerPartition) {
		this.dataset = dataset;
		this.residualMode = residualMode;
		this.minRowsPerPartition = minRowsPerPartition;
	}

	/**
	 * Learns {@code target}'s table from the data this learner was constructed with.
	 * <br>
	 * Does not itself write anything back to the model - see {@link RegressionTableWriter} for applying a
	 * NodeLearningResult to the target Node.
	 *
	 * @param target the continuous node to learn; must already have passed {@link RegressionEligibility#evaluate}
	 *
	 * @return the learning outcome
	 */
	public NodeLearningResult learn(Node target) {

		RegressionEligibility.Decision eligibility = RegressionEligibility.evaluate(target);
		if (!eligibility.isEligible()){
			return new NodeLearningResult(target, true, eligibility.getReason(), null, null);
		}

		if (NodeRole.of(target) != NodeRole.CONTINUOUS){
			return new NodeLearningResult(target, true, "Node '" + target.getId() + "' is categorical; this learner only handles continuous targets", null, null);
		}

		Set<Node> allParents = target.getParents();
		List<Node> continuousParents = allParents.stream()
				.filter(parent -> NodeRole.of(parent) == NodeRole.CONTINUOUS)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());
		List<Node> categoricalParents = allParents.stream()
				.filter(parent -> NodeRole.of(parent) == NodeRole.CATEGORICAL)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());

		List<String> continuousParentIds = continuousParents.stream().map(Node::getId).collect(Collectors.toList());

		if (categoricalParents.isEmpty()){
			PartitionResult result = fitSingle(target, continuousParents, continuousParentIds);
			List<PartitionResult> results = new ArrayList<>();
			results.add(result);
			return new NodeLearningResult(target, false, null, categoricalParents, results);
		}

		List<PartitionEnumerator.Combination> combinations = PartitionEnumerator.enumerate(categoricalParents);
		PooledAncovaFit pooledFit = null;

		List<PartitionResult> results = new ArrayList<>();
		for (PartitionEnumerator.Combination combination : combinations){
			RegressionDataset.Selection selection = dataset.selectRows(target.getId(), continuousParentIds, combination);

			if (isSufficient(selection.getN(), continuousParents.size())){
				OrdinaryLeastSquares.Result fit = OrdinaryLeastSquares.fit(selection.getX(), selection.getY());
				if (fit.isFullRank()){
					results.add(toPartitionResult(target, combination, continuousParents, fit.getIntercept(), sliceSlopes(fit), selection.getY(), fit.getN(), fit.getR2(), fit.getResidualVariance(), FitSource.PARTITION_SPECIFIC));
					continue;
				}
			}

			// Partition-specific fit wasn't feasible: fall back to a pooled ANCOVA fit shared across all partitions
			if (pooledFit == null){
				pooledFit = fitPooledAncova(target, continuousParents, continuousParentIds, categoricalParents, combinations);
			}

			if (pooledFit != null && pooledFit.fullRank){
				double intercept = pooledFit.baseIntercept + pooledFit.combinationIntercepts.getOrDefault(combination, 0.0);
				results.add(toPartitionResult(target, combination, continuousParents, intercept, pooledFit.continuousSlopes, null, pooledFit.n, pooledFit.r2, pooledFit.residualVariance, FitSource.POOLED_ANCOVA));
				continue;
			}

			// Even the pooled fit was infeasible: fall back to the global mean of the target over all available rows
			GlobalMeanFit globalMeanFit = fitGlobalMean(target);
			results.add(toPartitionResult(target, combination, continuousParents, globalMeanFit.mean, zeros(continuousParents.size()), null, globalMeanFit.n, Double.NaN, globalMeanFit.variance, FitSource.GLOBAL_MEAN));
		}

		return new NodeLearningResult(target, false, null, categoricalParents, results);
	}

	private PartitionResult fitSingle(Node target, List<Node> continuousParents, List<String> continuousParentIds) {
		RegressionDataset.Selection selection = dataset.selectRows(target.getId(), continuousParentIds, null);

		if (isSufficient(selection.getN(), continuousParents.size())){
			OrdinaryLeastSquares.Result fit = OrdinaryLeastSquares.fit(selection.getX(), selection.getY());
			if (fit.isFullRank()){
				return toPartitionResult(target, null, continuousParents, fit.getIntercept(), sliceSlopes(fit), selection.getY(), fit.getN(), fit.getR2(), fit.getResidualVariance(), FitSource.PARTITION_SPECIFIC);
			}
		}

		GlobalMeanFit globalMeanFit = fitGlobalMean(target);
		return toPartitionResult(target, null, continuousParents, globalMeanFit.mean, zeros(continuousParents.size()), null, globalMeanFit.n, Double.NaN, globalMeanFit.variance, FitSource.GLOBAL_MEAN);
	}

	private boolean isSufficient(int n, int k) {
		int requiredForDf = k + 2; // at least 1 residual degree of freedom
		return n >= Math.max(minRowsPerPartition, requiredForDf);
	}

	private double[] sliceSlopes(OrdinaryLeastSquares.Result fit) {
		double[] coefficients = fit.getCoefficients();
		double[] slopes = new double[coefficients.length - 1];
		System.arraycopy(coefficients, 1, slopes, 0, slopes.length);
		return slopes;
	}

	private double[] zeros(int size) {
		return new double[size];
	}

	private static class GlobalMeanFit {

		double mean;
		double variance;
		int n;
	}

	private GlobalMeanFit fitGlobalMean(Node target) {
		RegressionDataset.Selection selection = dataset.selectRows(target.getId(), new ArrayList<>(), null);
		GlobalMeanFit result = new GlobalMeanFit();
		result.n = selection.getN();
		if (selection.getN() == 0){
			result.mean = 0;
			result.variance = Double.NaN;
			return result;
		}
		OrdinaryLeastSquares.Result fit = OrdinaryLeastSquares.fit(selection.getX(), selection.getY());
		result.mean = fit.getIntercept();
		result.variance = fit.getResidualVariance();
		return result;
	}

	private static class PooledAncovaFit {

		double baseIntercept;
		java.util.Map<PartitionEnumerator.Combination, Double> combinationIntercepts;
		double[] continuousSlopes;
		double r2;
		double residualVariance;
		int n;
		boolean fullRank;
	}

	/**
	 * Fits one shared regression across ALL of the target's rows regardless of partition, using the continuous
	 * parents as ordinary regressors plus one dummy variable per non-reference partition combination (the first
	 * combination is the implicit reference, absorbed into the intercept).
	 */
	private PooledAncovaFit fitPooledAncova(Node target, List<Node> continuousParents, List<String> continuousParentIds, List<Node> categoricalParents, List<PartitionEnumerator.Combination> combinations) {

		List<String> categoricalParentIds = categoricalParents.stream().map(Node::getId).collect(Collectors.toList());

		// Rows where target + continuous parents are present, and every categorical parent has some known state
		// (not necessarily matching the target combination - the combination itself becomes a regressor here).
		RegressionDataset.PooledSelection pooled = dataset.selectPooledRows(target.getId(), continuousParentIds, categoricalParentIds, combinations);

		PooledAncovaFit result = new PooledAncovaFit();
		result.combinationIntercepts = new java.util.HashMap<>();

		int k = continuousParents.size() + Math.max(0, combinations.size() - 1);
		if (pooled.getN() < Math.max(minRowsPerPartition, k + 2)){
			result.fullRank = false;
			return result;
		}

		OrdinaryLeastSquares.Result fit = OrdinaryLeastSquares.fit(pooled.getX(), pooled.getY());
		result.fullRank = fit.isFullRank();
		if (!result.fullRank){
			return result;
		}

		result.n = fit.getN();
		result.r2 = fit.getR2();
		result.residualVariance = fit.getResidualVariance();
		result.baseIntercept = fit.getIntercept();
		result.continuousSlopes = new double[continuousParents.size()];
		System.arraycopy(fit.getCoefficients(), 1, result.continuousSlopes, 0, continuousParents.size());

		for (int i = 1; i < combinations.size(); i++){
			double dummyCoefficient = fit.getCoefficients()[1 + continuousParents.size() + (i - 1)];
			result.combinationIntercepts.put(combinations.get(i), dummyCoefficient);
		}

		return result;
	}

	private PartitionResult toPartitionResult(Node target, PartitionEnumerator.Combination combination, List<Node> continuousParents, double intercept, double[] slopes, double[] yForVarianceFloor, int n, double r2, double residualVariance, FitSource fitSource) {
		String expression = buildExpression(target, intercept, continuousParents, slopes, residualVariance, yForVarianceFloor);
		return new PartitionResult(combination, expression, n, r2, residualVariance, fitSource);
	}

	private String buildExpression(Node target, double intercept, List<Node> continuousParents, double[] slopes, double residualVariance, double[] yForVarianceFloor) {
		String meanExpression = buildMeanExpression(intercept, continuousParents, slopes);
		double effectiveVariance = effectiveVariance(residualVariance, yForVarianceFloor);

		if (target.getType() == Node.Type.Ranked){
			double[] bounds = getBounds(target);
			return "TNormal(" + meanExpression + ", " + formatNumber(effectiveVariance) + ", "
					+ formatNumber(bounds[0]) + ", " + formatNumber(bounds[1]) + ")";
		}

		if (residualMode == ResidualMode.ARITHMETIC){
			return "Arithmetic(" + meanExpression + ")";
		}

		return "Normal(" + meanExpression + ", " + formatNumber(effectiveVariance) + ")";
	}

	/**
	 * A Ranked node's overall lower/upper bound, spanning all its states (its first state's lower bound to its
	 * last state's upper bound) - needed because {@code TNormal} takes explicit bounds, unlike {@code Normal}.
	 */
	private double[] getBounds(Node target) {
		@SuppressWarnings("unchecked")
		List<ExtendedState> states = (List<ExtendedState>) target.getLogicNode().getExtendedStates();
		double lower = states.get(0).getRange().getLowerBound();
		double upper = states.get(states.size() - 1).getRange().getUpperBound();
		return new double[]{lower, upper};
	}

	private double effectiveVariance(double residualVariance, double[] yForVarianceFloor) {
		if (!Double.isNaN(residualVariance) && residualVariance > 0){
			return residualVariance;
		}
		double dataVariance = (yForVarianceFloor != null) ? variance(yForVarianceFloor) : 0;
		double floor = Math.max(dataVariance * 1e-6, 1e-9);
		return floor;
	}

	private double variance(double[] values) {
		if (values.length == 0){
			return 0;
		}
		double mean = 0;
		for (double v : values){
			mean += v;
		}
		mean /= values.length;
		double sumSq = 0;
		for (double v : values){
			double d = v - mean;
			sumSq += d * d;
		}
		return sumSq / values.length;
	}

	private String buildMeanExpression(double intercept, List<Node> continuousParents, double[] slopes) {
		StringBuilder sb = new StringBuilder(formatNumber(intercept));
		for (int i = 0; i < continuousParents.size(); i++){
			double coef = slopes[i];
			if (coef >= 0){
				sb.append(" + ").append(formatNumber(coef));
			}
			else {
				sb.append(" - ").append(formatNumber(-coef));
			}
			sb.append("*").append(continuousParents.get(i).getId());
		}
		return sb.toString();
	}

	private static String formatNumber(double value) {
		if (value == 0){
			return "0";
		}
		BigDecimal bd = BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
		String plain = bd.toPlainString();
		return plain;
	}
}
