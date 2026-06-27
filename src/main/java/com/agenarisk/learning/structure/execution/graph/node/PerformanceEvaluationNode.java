package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.PerformanceEvaluationConfigurer;
import com.agenarisk.learning.structure.config.PerformanceEvaluationExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.result.PerformanceEvaluation;
import com.agenarisk.learning.structure.result.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class PerformanceEvaluationNode extends EvaluationNode {

	private List<String> models = new ArrayList<>();
	private String dataSource;
	private String target = "";
	private boolean calculateRoc = false;
	private String valueSeparator = ",";

	@Override
	public String getSubType() {
		return "performanceEvaluation";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		JSONArray jModels = jOptions.optJSONArray("models");
		if (jModels != null) {
			for (int i = 0; i < jModels.length(); i++) {
				models.add(jModels.getString(i));
			}
		}
		dataSource = jOptions.optString("dataSource", "");
		target = jOptions.optString("target", "");
		calculateRoc = jOptions.optBoolean("calculateRoc", false);
		valueSeparator = jOptions.optString("valueSeparator", ",");
		parseOutputFileOptions(jOptions);
	}

	@Override
	public Set<String> getInputLabels() {
		Set<String> labels = new LinkedHashSet<>(models);
		if (dataSource != null && !dataSource.isEmpty()) {
			labels.add(dataSource);
		}
		return labels;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void execute(GraphExecutionContext ctx) {
		try {
			DataSourceNode dsNode = (DataSourceNode) ctx.getNode(dataSource);
			Path dataPath = dsNode.resolvedPath(ctx);
			Path outputDirPath = ctx.getOutputDirPath();

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			// Build a modelPrefixes map of successful or warning model nodes (label → label)
			Map<String, String> modelPrefixes = new LinkedHashMap<>();
			for (String modelLabel : models) {
				GraphNode parent = ctx.getNode(modelLabel);
				if (parent instanceof ModelNode && (parent.getStatus() == Status.success || parent.getStatus() == Status.warning)) {
					modelPrefixes.put(modelLabel, modelLabel);
				}
				else {
					BLogger.logConditional("Skipping model '" + modelLabel + "' for performance evaluation (not available or failed)");
				}
			}

			Result tmpResult = new Result();

			PerformanceEvaluationConfigurer configurer = new PerformanceEvaluationConfigurer(config);
			JSONObject jParams = new JSONObject();
			jParams.put("dataPath", dataPath.toString());
			jParams.put("target", target);
			jParams.put("calculateRoc", calculateRoc);
			jParams.put("valueSeparator", valueSeparator);
			configurer.configureFromJson(new JSONObject().put("parameters", jParams));
			configurer.setOutputDirPath(outputDirPath);
			configurer.setModelPrefixes(modelPrefixes);
			configurer.setStageLabel(getLabel());
			configurer.setPipelineResult(tmpResult);

			PerformanceEvaluationExecutor executor = configurer.apply();
			executor.execute();

			JSONArray results = new JSONArray();
			boolean anySuccess = false;
			boolean anyFailure = false;
			for (PerformanceEvaluation pe : tmpResult.getPerformanceEvaluations()) {
				results.put(pe.toJson());
				if (pe.isSuccess()) {
					anySuccess = true;
				}
				else {
					anyFailure = true;
				}
			}

			Path evalPath = getEvalPath(ctx);
			Files.write(evalPath, results.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			writeOutputFileIfRequested(ctx, results);
			setResult(results);
			if (anySuccess && !anyFailure) {
				setStatus(Status.success);
			}
			else if (anySuccess) {
				setStatus(Status.warning);
				setStatusMessage("Some model performance evaluations failed");
			}
			else {
				failWith("All model performance evaluations failed", null);
			}
		}
		catch (Exception ex) {
			failWith("Performance evaluation failed: " + ex.getMessage(), ex);
		}
	}

	@Override
	protected List<List<Object>> toCsvRows(JSONArray results) {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(Arrays.asList("modelLabel", "success", "absoluteError", "brierScore", "sphericalScore", "macroAuc", "microAuc"));
		for (int i = 0; i < results.length(); i++) {
			JSONObject entry = results.optJSONObject(i);
			if (entry == null) continue;
			rows.add(Arrays.asList(
				entry.optString("modelLabel"),
				entry.optBoolean("success"),
				entry.opt("absoluteError"),
				entry.opt("brierScore"),
				entry.opt("sphericalScore"),
				entry.opt("macroAuc"),
				entry.opt("microAuc")
			));
		}
		return rows;
	}

	public List<String> getModels() {
		return models;
	}

	public String getDataSource() {
		return dataSource;
	}
}
