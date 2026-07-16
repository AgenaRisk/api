package com.agenarisk.learning.structure.config;

import com.agenarisk.api.exception.InconsistentEvidenceException;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.ResultValue;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import com.agenarisk.learning.structure.result.PerformanceEvaluation;
import com.agenarisk.learning.structure.logger.BLogger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;

/**
 *
 * @author Eugene Dementiev
 */
public class PerformanceEvaluationExecutor extends Configurer<PerformanceEvaluationExecutor> implements Executable {

	private PerformanceEvaluationConfigurer originalConfigurer;
	private int stageIndex = 0;

	protected PerformanceEvaluationExecutor(Config config) {
		super(config);
	}

	protected PerformanceEvaluationExecutor() {
		super();
	}

	public void setOriginalConfigurer(PerformanceEvaluationConfigurer originalConfigurer) {
		this.originalConfigurer = originalConfigurer;
	}

	@Override
	public void execute() throws StructureLearningException {
		BLogger.logConditional("Beginning performance evaluation, stage: " + stageIndex);

		if (originalConfigurer == null){
			BLogger.logConditional("Original performance evaluation configurer not set");
			return;
		}

		BLogger.logConditional("Performance evaluation label: " + originalConfigurer.getStageLabel());
		try {
			Path csvPath = originalConfigurer.getDataPath();
			BLogger.logConditional("Loading data from " + csvPath);
			List<List<String>> data = CsvReader.readCsv(csvPath, originalConfigurer.getValueSeparator());

			if (data.size() < 2){
				throw new StructureLearningException("Validation data file does not contain case data");
			}
			List<String> dataHeaders = data.get(0);
			// Drop only the header row; subList's end index is exclusive.
			data = data.subList(1, data.size());

			// Optional deterministic row subsampling, to bound runtime when
			// evaluating many targets. Same subset is used for all models/targets.
			int maxRows = originalConfigurer.getMaxRows();
			if (maxRows > 0 && data.size() > maxRows){
				List<List<String>> shuffled = new ArrayList<>(data);
				Collections.shuffle(shuffled, new Random(42));
				data = new ArrayList<>(shuffled.subList(0, maxRows));
				BLogger.logConditional("Subsampled evaluation data to " + maxRows + " rows");
			}

			List<String> targets = originalConfigurer.getTargets();

			for (String modelFilePrefix: originalConfigurer.getModelPrefixes().keySet()){
				// Model-level (aggregate) result added to the pipeline output.
				PerformanceEvaluation evaluation = new PerformanceEvaluation();
				originalConfigurer.getPipelineResult().getPerformanceEvaluations().add(evaluation);
				evaluation.setLabel(originalConfigurer.getStageLabel());
				evaluation.setModelLabel(originalConfigurer.getModelPrefixes().get(modelFilePrefix));
				BLogger.logConditional("Evaluating " + evaluation.getModelLabel());

				try {
					Path modelPath = originalConfigurer.getOutputDirPath().resolve(modelFilePrefix + ".cmpx");
					Model model = Model.loadModel(modelPath.toString());
					DataSet dataCase = model.getDataSetList().get(0);
					Network network = model.getNetworkList().get(0);

					// Targets must be all continuous or all discrete: the two use entirely different metrics
					// (MAE/RMSE/CRPS vs Brier/spherical/ROC-AUC) that can't be meaningfully averaged together
					// into one model-level aggregate.
					List<String> continuousTargets = new ArrayList<>();
					List<String> discreteTargets = new ArrayList<>();
					for (String targetId : targets){
						Node t = network.getNode(targetId);
						if (t == null){
							continue; // reported per-target as "not found" below
						}
						(isContinuousTargetType(t) ? continuousTargets : discreteTargets).add(targetId);
					}
					if (!continuousTargets.isEmpty() && !discreteTargets.isEmpty()){
						throw new StructureLearningException("Performance evaluation targets must be all continuous "
								+ "or all discrete, not a mix. Continuous: " + continuousTargets + ". Discrete: " + discreteTargets + ".");
					}

					List<PerformanceEvaluation> successfulPerfEvaluations = new ArrayList<>();
					for (String targetId : targets){
						PerformanceEvaluation te = evaluateOneTarget(model, network, dataCase, data, dataHeaders, targetId);
						te.setLabel(originalConfigurer.getStageLabel());
						te.setModelLabel(evaluation.getModelLabel());
						evaluation.getTargetResults().add(te);
						if (te.isSuccess()){
							successfulPerfEvaluations.add(te);
						}
					}

					if (successfulPerfEvaluations.isEmpty()){
						evaluation.setSuccess(false);
						String firstMsg = evaluation.getTargetResults().stream()
								.map(PerformanceEvaluation::getMessage)
								.filter(m -> m != null && !m.isEmpty())
								.findFirst().orElse("All targets failed to evaluate");
						evaluation.setMessage(firstMsg);
					}
					else {
						// Macro aggregate: mean of per-target means, per metric (each
						// metric only ever averages with itself, so directions are kept).
						evaluation.setSuccess(true);
						evaluation.setTargetKind(successfulPerfEvaluations.get(0).getTargetKind());
						evaluation.setAbsoluteError(mean(successfulPerfEvaluations, PerformanceEvaluation::getAbsoluteError));
						evaluation.setBrierScore(mean(successfulPerfEvaluations, PerformanceEvaluation::getBrierScore));
						evaluation.setSphericalScore(mean(successfulPerfEvaluations, PerformanceEvaluation::getSphericalScore));

						OptionalDouble mae = successfulPerfEvaluations.stream()
								.map(PerformanceEvaluation::getMae).filter(Objects::nonNull)
								.mapToDouble(Double::doubleValue).average();
						if (mae.isPresent()){
							evaluation.setMae(mae.getAsDouble());
						}

						OptionalDouble macro = successfulPerfEvaluations.stream()
								.map(PerformanceEvaluation::getMacroAuc).filter(Objects::nonNull)
								.mapToDouble(Double::doubleValue).average();
						if (macro.isPresent()){
							evaluation.setMacroAuc(macro.getAsDouble());
						}
						OptionalDouble micro = successfulPerfEvaluations.stream()
								.map(PerformanceEvaluation::getMicroAuc).filter(Objects::nonNull)
								.mapToDouble(Double::doubleValue).average();
						if (micro.isPresent()){
							evaluation.setMicroAuc(micro.getAsDouble());
						}

						OptionalDouble rmse = successfulPerfEvaluations.stream()
								.map(PerformanceEvaluation::getRmse).filter(Objects::nonNull)
								.mapToDouble(Double::doubleValue).average();
						if (rmse.isPresent()){
							evaluation.setRmse(rmse.getAsDouble());
						}
						OptionalDouble crps = successfulPerfEvaluations.stream()
								.map(PerformanceEvaluation::getCrps).filter(Objects::nonNull)
								.mapToDouble(Double::doubleValue).average();
						if (crps.isPresent()){
							evaluation.setCrps(crps.getAsDouble());
						}

						// For a single target, surface its ROC curves/AUCs on the
						// model-level result too, so the existing ROC viewer still works.
						// (For multiple targets they remain under each per-target result.)
						if (successfulPerfEvaluations.size() == 1){
							successfulPerfEvaluations.get(0).getRocAucs().forEach(evaluation::addRocAuc);
							successfulPerfEvaluations.get(0).getRocPoints().forEach(evaluation::addRocCurve);
						}

						if (successfulPerfEvaluations.size() < targets.size()){
							evaluation.setMessage((targets.size() - successfulPerfEvaluations.size()) + " of " + targets.size()
									+ " targets failed to evaluate");
						}
					}
				}
				catch (Exception ex){
					evaluation.setSuccess(false);
					String message = "Model '" + evaluation.getModelLabel() + "': " + GraphNode.friendlyMessage(ex);
					BLogger.logConditional(message);
					evaluation.setMessage(evaluation.getMessage().isEmpty() ? message : message + "; also: " + evaluation.getMessage());
				}
			}
		}
		catch (Exception ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

	private static double mean(List<PerformanceEvaluation> list, java.util.function.ToDoubleFunction<PerformanceEvaluation> f) {
		return list.stream().mapToDouble(f).average().orElse(0.0);
	}

	/**
	 * ContinuousInterval/IntegerInterval nodes carry range-string state labels (dynamically discretized for
	 * simulated nodes) rather than the literal values case data contains - see {@code State.computeLabel}.
	 * Ranked/DiscreteReal/Labelled/Boolean all get literal, CSV-matchable labels and stay on the discrete path.
	 */
	private static boolean isContinuousTargetType(Node node) {
		return node.getType() == Node.Type.ContinuousInterval || node.getType() == Node.Type.IntegerInterval;
	}

	/**
	 * Evaluate a single target: for each case, clear evidence, observe every other
	 * variable, predict the target, and score it. Returns a per-target result
	 * (metrics are row-means). Never throws for a bad target — marks it failed so
	 * other targets can continue.
	 */
	@SuppressWarnings("unchecked")
	private PerformanceEvaluation evaluateOneTarget(
			Model model,
			Network network,
			DataSet dataCase,
			List<List<String>> data,
			List<String> dataHeaders,
			String targetId) {
		PerformanceEvaluation evaluation = new PerformanceEvaluation();
		evaluation.setTarget(targetId);

		Node targetNode = network.getNode(targetId);
		if (targetNode == null){
			evaluation.setSuccess(false);
			evaluation.setMessage("Target node '" + targetId + "' not found in model");
			return evaluation;
		}

		// ContinuousInterval/IntegerInterval nodes carry range-string state labels
		// (e.g. "240.0 - 250.0", dynamically discretized for simulated nodes) that a
		// raw numeric CSV value can never match by exact string equality - unlike
		// Boolean/Labelled/Ranked/DiscreteReal, whose state labels ARE the literal
		// values case data is expected to contain (see State.computeLabel). So these
		// two node types get a separate, numeric evaluation path (MAE/RMSE/CRPS
		// against the predicted mean/stddev) instead of the classification metrics
		// (Brier/spherical/ROC-AUC), which have no meaningful continuous analogue.
		boolean numericTarget = isContinuousTargetType(targetNode);
		evaluation.setTargetKind(numericTarget ? "continuous" : "discrete");
		List<String> targetNodeStates = numericTarget
				? Collections.emptyList()
				: targetNode.getStates().stream().map(s -> s.getLabel()).collect(Collectors.toList());

		// Only the target's own direct parents are entered as evidence, not the whole row. Entering every
		// other column as hard evidence routinely produces mutually-inconsistent evidence once several
		// continuous nodes each carry their own learned (fitted, not exact) regression relationship - the
		// row is fine as an observation of the true joint, but the *learned* network's tightly-fitted
		// conditional distributions frequently give it near-zero (or exactly zero, once several such
		// constraints compound) joint probability. Evaluating a prediction only needs the target's Markov
		// blanket boundary anyway - its parents fully determine (or, in the discrete case, condition) the
		// predictive distribution the model would compute for the target from these inputs, so restricting
		// evidence entry to just those columns matches standard regression-evaluation practice (predict from
		// features, not from every other observed variable) while sidestepping the inconsistency almost
		// entirely.
		Set<String> parentIds = targetNode.getParents().stream().map(Node::getId).collect(Collectors.toSet());

		// Accumulate from zero (the metric fields default to worst-case values,
		// which must not be folded into the running sum).
		double sumAbs = 0, sumBrier = 0, sumSph = 0, sumSq = 0, sumCrps = 0;
		int successRows = 0;

		for (int rowIndex = 0; rowIndex < data.size(); rowIndex += 1){
			List<String> row = data.get(rowIndex);
			try {
				// Reset evidence from the previous case so a cell that fails to
				// enter cannot leave a stale observation on this row.
				dataCase.clearObservations();
				String actualValue = "";
				for (int observationIndex = 0; observationIndex < row.size(); observationIndex += 1){
					String nodeId = dataHeaders.get(observationIndex);
					String value = row.get(observationIndex);
					if (Objects.equals(nodeId, targetNode.getId())){
						actualValue = value;
						if (!numericTarget && !targetNodeStates.contains(actualValue)){
							throw new StructureLearningException("Target node states does not contain actual node state from case data");
						}
						continue;
					}
					if (!parentIds.contains(nodeId)){
						// Not a direct parent of the target - irrelevant to its prediction, and entering it
						// as evidence only adds another chance of conflicting with the learned model.
						continue;
					}
					try {
						dataCase.setObservation(network.getNode(nodeId), value);
					}
					catch (Exception ex){
						if (ex.getCause() instanceof ExtendedStateNotFoundException){
							evaluation.setMessage("Evaluation case data contains states that are missing in model, e.g. " + value + " in " + nodeId);
						}
						else {
							evaluation.setMessage("Some evaluation case data failed to enter the model, e.g. row " + rowIndex + ": " + ex.getMessage());
						}
					}
				}

				if (actualValue == null || actualValue.isEmpty()){
					throw new StructureLearningException("Actual value of target node missing from case data");
				}

				try {
					model.calculate();
				}
				catch (InconsistentEvidenceException ex){
					throw new StructureLearningException("Target's parent values in this case are jointly inconsistent with the learned model (row "
							+ rowIndex + "): " + ex.getMessage());
				}

				CalculationResult predictedDistribution = dataCase.getCalculationResult(targetNode);

				if (numericTarget){
					double actualNumeric;
					try {
						actualNumeric = Double.parseDouble(actualValue);
					}
					catch (NumberFormatException ex){
						throw new StructureLearningException("Actual value of target node is not numeric: " + actualValue);
					}
					double predictedMean = predictedDistribution.getMean();
					double predictedStdDev = predictedDistribution.getStandardDeviation();
					double error = actualNumeric - predictedMean;
					sumAbs += Math.abs(error);
					sumSq += error * error;
					sumCrps += calculateCrps(predictedMean, predictedStdDev, actualNumeric);
				}
				else {
					Map<String, Double> predictedDistributionMap = predictedDistribution.getResultValues().stream().collect(Collectors.toMap(ResultValue::getLabel, ResultValue::getValue));
					ResultValue predictedValue = predictedDistribution.getResultValue(actualValue);
					if (predictedValue == null){
						throw new StructureLearningException("Actual value of target node missing from case data");
					}
					sumAbs += 1 - predictedValue.getValue();
					sumBrier += calculateBrierScore(actualValue, predictedDistributionMap);
					sumSph += calculateSphericalScore(actualValue, predictedDistributionMap);

					if (originalConfigurer.isCalculateRoc()){
						for (String classLabel : targetNodeStates) {
							double predictedProb = predictedDistributionMap.getOrDefault(classLabel, 0.0);
							evaluation.getRocScores().computeIfAbsent(classLabel, k -> new ArrayList<>()).add(predictedProb);
							evaluation.getRocTruths().computeIfAbsent(classLabel, k -> new ArrayList<>()).add(actualValue.equals(classLabel) ? 1 : 0);
						}
					}
				}

				successRows += 1;
			}
			catch (Exception ex){
				String message = "Failed to calculate case #" + rowIndex + " for target '" + targetId + "': " + ex.getMessage();
				evaluation.setMessage(message);
				BLogger.logConditional(message);
			}
		}

		if (successRows == 0){
			evaluation.setSuccess(false);
			if (evaluation.getMessage().isEmpty()){
				evaluation.setMessage("All cases failed to calculate for target '" + targetId + "'");
			}
			return evaluation;
		}

		if (numericTarget){
			evaluation.setMae(sumAbs / successRows);
			evaluation.setRmse(Math.sqrt(sumSq / successRows));
			evaluation.setCrps(sumCrps / successRows);
			evaluation.setSuccess(true);
			return evaluation;
		}

		if (originalConfigurer.isCalculateRoc()){
			List<Double> allAucs = new ArrayList<>();
			List<Double> allScores = new ArrayList<>();
			List<Integer> allTruths = new ArrayList<>();
			for (String classLabel : targetNodeStates) {
				List<Double> scores = evaluation.getRocScores().get(classLabel);
				List<Integer> truths = evaluation.getRocTruths().get(classLabel);
				if (scores != null && truths != null && scores.size() == truths.size()) {
					double auc = computeAUC(scores, truths);
					evaluation.addRocAuc(classLabel, auc);
					allAucs.add(auc);
					allScores.addAll(scores);
					allTruths.addAll(truths);
					evaluation.addRocCurve(classLabel, computeRocCurve(scores, truths, 1000));
				}
			}
			if (!allAucs.isEmpty()) {
				evaluation.setMacroAuc(allAucs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
			}
			if (!allScores.isEmpty() && allScores.size() == allTruths.size()) {
				evaluation.setMicroAuc(computeAUC(allScores, allTruths));
			}
		}

		evaluation.setAbsoluteError(sumAbs / successRows);
		evaluation.setBrierScore(sumBrier / successRows);
		evaluation.setSphericalScore(sumSph / successRows);
		evaluation.setSuccess(true);
		return evaluation;
	}

	/**
     * Calculates the Brier score for a single multiclass prediction.
     *
     * @param actualState           The true class label.
     * @param predictedDistribution A map from class labels to predicted probabilities.
     * @return The Brier score for the prediction.
     * @throws IllegalArgumentException if the predicted distribution does not sum to 1 (±0.01).
     */
    public static double calculateBrierScore(String actualState, Map<String, Double> predictedDistribution) {
        double score = 0.0;

        double totalProb = predictedDistribution.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(totalProb - 1.0) > 0.01) {
            throw new IllegalArgumentException("Predicted probabilities must sum to 1. Current sum: " + totalProb);
        }

        for (Map.Entry<String, Double> entry : predictedDistribution.entrySet()) {
            String label = entry.getKey();
            double predictedProb = entry.getValue();
            double actual = label.equals(actualState) ? 1.0 : 0.0;
            score += Math.pow(predictedProb - actual, 2);
        }

        return score;
    }

	    /**
     * Calculates the Spherical score for a single multiclass probabilistic prediction.
     *
     * @param actualState           The true class label.
     * @param predictedDistribution A map of class labels to predicted probabilities.
     * @return The Spherical score.
     * @throws IllegalArgumentException if the predicted distribution does not contain the actual class.
     */
    public static double calculateSphericalScore(String actualState, Map<String, Double> predictedDistribution) {
        if (!predictedDistribution.containsKey(actualState)) {
            throw new IllegalArgumentException("Predicted distribution does not contain the actual state: " + actualState);
        }

        double pTrue = predictedDistribution.get(actualState);
        double norm = Math.sqrt(predictedDistribution.values().stream()
                .mapToDouble(p -> p * p)
                .sum());

        return pTrue / norm;
    }

	/**
	 * Continuous Ranked Probability Score for a Normal(mean, stdDev) predictive distribution against a single
	 * observed numeric value - the continuous analogue of the Brier score (it scores the whole predictive
	 * distribution, rewarding well-calibrated uncertainty as well as accuracy, not just the point estimate).
	 * <br>
	 * Closed form: CRPS(N(mean, stdDev^2), x) = stdDev * [ z*(2*Phi(z) - 1) + 2*phi(z) - 1/sqrt(pi) ], z = (x - mean) / stdDev.
	 * <br>
	 * Rather than trying to detect ahead of time whether a node's expression is stochastic (Normal) or deterministic
	 * (Arithmetic) - which wouldn't even be reliable, since a technically-Normal node can still have near-zero
	 * predictive variance for a given case if its parents are fully observed - this simply falls back to the exact
	 * mathematical limit of the closed form as stdDev -&gt; 0, which is the absolute error. That limit is what a
	 * deterministic (or effectively deterministic, for this case) prediction should score anyway.
	 *
	 * @param mean predicted mean
	 * @param stdDev predicted standard deviation; treated as 0 (falls back to absolute error) below a small epsilon
	 * @param actual observed value
	 *
	 * @return the CRPS, lower is better, 0 for a perfect deterministic match
	 */
	public static double calculateCrps(double mean, double stdDev, double actual) {
		if (stdDev < 1e-9){
			return Math.abs(actual - mean);
		}
		double z = (actual - mean) / stdDev;
		double cdf = cern.jet.stat.Probability.normal(mean, stdDev * stdDev, actual);
		double pdf = Math.exp(-0.5 * z * z) / Math.sqrt(2 * Math.PI);
		return stdDev * (z * (2 * cdf - 1) + 2 * pdf - 1 / Math.sqrt(Math.PI));
	}

	private double computeAUC(List<Double> scores, List<Integer> truths) {
		List<int[]> pairs = new ArrayList<>();
		for (int i = 0; i < scores.size(); i++) {
			pairs.add(new int[] { i, truths.get(i) });
		}
		pairs.sort((a, b) -> Double.compare(scores.get(b[0]), scores.get(a[0]))); // descending order

		int tp = 0, fp = 0;
		int posCount = 0, negCount = 0;
		for (int t : truths) {
			if (t == 1) posCount++; else negCount++;
		}

		double auc = 0.0;
		for (int[] pair : pairs) {
			int actual = pair[1];
			if (actual == 1) {
				tp++;
			} else {
				auc += tp;
				fp++;
			}
		}
		if (posCount == 0 || negCount == 0) return 0.0;
		return auc / (posCount * (double) negCount);
	}

	private List<double[]> computeRocCurve(List<Double> scores, List<Integer> truths, int steps) {
		List<double[]> curve = new ArrayList<>();
		int totalPos = 0, totalNeg = 0;
		for (int label : truths) {
			if (label == 1) totalPos++;
			else totalNeg++;
		}

		for (int i = 0; i <= steps; i++) {
			double threshold = i / (double) steps;
			int tp = 0, fp = 0;
			for (int j = 0; j < scores.size(); j++) {
				double score = scores.get(j);
				int actual = truths.get(j);
				if (score >= threshold) {
					if (actual == 1) tp++;
					else fp++;
				}
			}
			double tpr = totalPos == 0 ? 0.0 : tp / (double) totalPos;
			double fpr = totalNeg == 0 ? 0.0 : fp / (double) totalNeg;
			curve.add(new double[] { fpr, tpr });
		}
		return curve;
	}

	public void setStageIndex(int stageIndex) {
		this.stageIndex = stageIndex;
	}

}
