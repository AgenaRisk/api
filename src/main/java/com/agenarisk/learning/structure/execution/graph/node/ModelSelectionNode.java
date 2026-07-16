package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModelSelectionNode extends ModelNode {

	public enum Direction {
		maximize, minimize
	}

	private String evaluation;
	private String metric;
	private Direction direction = Direction.maximize;
	// Whether the user's own config explicitly set a direction, vs. it just sitting at the class default -
	// distinguishes "user chose maximize" from "nothing chosen yet" so resolveDefaultMetric knows it's free
	// to pick a direction that actually matches whichever metric it infers.
	private boolean directionExplicit = false;

	@Override
	public String getSubType() {
		return "modelSelection";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.evaluation = jOptions.optString("evaluation", "");
		this.metric = jOptions.optString("metric", "");
		this.directionExplicit = jOptions.has("direction");
		try {
			this.direction = Direction.valueOf(jOptions.optString("direction", Direction.maximize.name()).toLowerCase());
		}
		catch (Exception ex) {
			this.direction = Direction.maximize;
		}
	}

	@Override
	public Set<String> getInputLabels() {
		Set<String> labels = new LinkedHashSet<>();
		if (evaluation != null && !evaluation.isEmpty()) {
			labels.add(evaluation);
		}
		return labels;
	}

	@Override
	public void execute(GraphExecutionContext ctx) {
		try {
			EvaluationNode evalNode = requireEvaluation(ctx, evaluation);
			Path evalJsonPath = evalNode.getEvalPath(ctx);

			if (!Files.exists(evalJsonPath)) {
				throw new StructureLearningException("Evaluation result file not found: " + evalJsonPath);
			}

			String evalJson = new String(Files.readAllBytes(evalJsonPath));
			JSONArray evalResults = new JSONArray(evalJson);

			if (metric == null || metric.isEmpty()) {
				// No metric configured (e.g. a Selection node connected and never opened in the UI) -
				// infer a sensible one from the evaluation results themselves, which by now always
				// reflect whichever kind of run actually happened, regardless of anything the UI did or
				// didn't do beforehand.
				resolveDefaultMetric(evalResults);
			}

			if (metric == null || metric.isEmpty()) {
				throw new StructureLearningException("metric is required for model selection, and no default "
						+ "could be inferred from the evaluation results (evaluation '" + evaluation + "' may have no successful entries)");
			}

			String bestLabel = null;
			double bestValue = direction == Direction.maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

			for (int i = 0; i < evalResults.length(); i++) {
				JSONObject entry = evalResults.optJSONObject(i);
				if (entry == null || !entry.has(metric)) {
					continue;
				}
				if (entry.isNull(metric)) {
					continue;
				}
				double value = entry.optDouble(metric, Double.NaN);
				if (Double.isNaN(value)) {
					continue;
				}

				boolean better = direction == Direction.maximize ? value > bestValue : value < bestValue;
				if (better) {
					bestValue = value;
					bestLabel = entry.optString("modelLabel");
				}
			}

			if (bestLabel == null) {
				throw new StructureLearningException("No valid entry found for metric '" + metric + "' in evaluation '" + evaluation + "'");
			}

			Path srcPath = ctx.modelPath(bestLabel);
			if (!Files.exists(srcPath)) {
				throw new StructureLearningException("Selected model file not found: " + srcPath);
			}

			Path destPath = getModelPath(ctx);
			Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);

			Model model = Model.loadModel(destPath.toString());
			setResult(model.toJson().optJSONObject("model"));
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Model selection failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Infers a default metric (and, if the user didn't explicitly set one, a matching direction) from the
	 * evaluation results, for a Selection node left unconfigured. Performance evaluation entries always carry
	 * a "targetKind" ("continuous"/"discrete") on every successful entry - CRPS for continuous (the proper
	 * scoring rule that accounts for predictive uncertainty, not just point accuracy), absoluteError for
	 * discrete (matching the pre-existing default for that case). Structure evaluation entries carry no
	 * "targetKind" but always have "bicScore". Leaves {@code metric} null (unchanged) if no entry has enough
	 * information to infer anything - the caller already handles that as a hard failure.
	 */
	void resolveDefaultMetric(JSONArray evalResults) {
		for (int i = 0; i < evalResults.length(); i++) {
			JSONObject entry = evalResults.optJSONObject(i);
			if (entry == null) {
				continue;
			}
			if (entry.has("targetKind")) {
				metric = "continuous".equals(entry.optString("targetKind", "")) ? "crps" : "absoluteError";
				if (!directionExplicit) {
					direction = Direction.minimize;
				}
				return;
			}
			if (entry.has("bicScore")) {
				metric = "bicScore";
				if (!directionExplicit) {
					direction = Direction.maximize;
				}
				return;
			}
		}
	}

	public String getEvaluation() {
		return evaluation;
	}

	public String getMetric() {
		return metric;
	}

	public Direction getDirection() {
		return direction;
	}
}
