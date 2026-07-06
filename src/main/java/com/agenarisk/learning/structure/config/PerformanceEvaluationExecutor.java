package com.agenarisk.learning.structure.config;

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
						evaluation.setAbsoluteError(mean(successfulPerfEvaluations, PerformanceEvaluation::getAbsoluteError));
						evaluation.setBrierScore(mean(successfulPerfEvaluations, PerformanceEvaluation::getBrierScore));
						evaluation.setSphericalScore(mean(successfulPerfEvaluations, PerformanceEvaluation::getSphericalScore));

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
		List<String> targetNodeStates = targetNode.getStates().stream().map(s -> s.getLabel()).collect(Collectors.toList());

		// Accumulate from zero (the metric fields default to worst-case values,
		// which must not be folded into the running sum).
		double sumAbs = 0, sumBrier = 0, sumSph = 0;
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
						if (!targetNodeStates.contains(actualValue)){
							throw new StructureLearningException("Target node states does not contain actual node state from case data");
						}
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

				model.calculate();

				CalculationResult predictedDistribution = dataCase.getCalculationResult(targetNode);
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
