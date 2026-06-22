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

	@Override
	public String getSubType() {
		return "modelSelection";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.evaluation = jOptions.optString("evaluation", "");
		this.metric = jOptions.optString("metric", "");
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
			if (metric == null || metric.isEmpty()) {
				throw new StructureLearningException("metric is required for model selection");
			}

			EvaluationNode evalNode = (EvaluationNode) ctx.getNode(evaluation);
			Path evalJsonPath = evalNode.getEvalPath(ctx);

			if (!Files.exists(evalJsonPath)) {
				throw new StructureLearningException("Evaluation result file not found: " + evalJsonPath);
			}

			String evalJson = new String(Files.readAllBytes(evalJsonPath));
			JSONArray evalResults = new JSONArray(evalJson);

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
