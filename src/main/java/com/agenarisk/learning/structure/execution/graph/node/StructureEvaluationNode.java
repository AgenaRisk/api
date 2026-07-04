package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.EvaluationConfigurer;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.utility.CmpxStructureExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class StructureEvaluationNode extends EvaluationNode {

	private List<String> models = new ArrayList<>();
	private String dataSource;
	private String bicLog = "2";

	@Override
	public String getSubType() {
		return "structureEvaluation";
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
		bicLog = jOptions.optString("bicLog", "2");
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
			DataSourceNode dsNode = requireDataSource(ctx, dataSource);
			Path dataPath = dsNode.resolvedPath(ctx);
			Path outputDirPath = ctx.getOutputDirPath();

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			config.setPathInput(dataPath.getParent().toString());
			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());
			config.setPathOutput(outputDirPath.toString());
			config.setEvalBic(true);

			EvaluationConfigurer evalConfigurer = new EvaluationConfigurer(config);
			JSONObject jParams = new JSONObject();
			jParams.put("bicLog", bicLog);
			jParams.put("logLikelihoodScore", true);
			evalConfigurer.configureFromJson(new JSONObject().put("parameters", jParams));

			JSONArray results = new JSONArray();
			boolean anySuccess = false;
			boolean anyFailure = false;

			for (String modelLabel : models) {
				GraphNode parent = ctx.getNode(modelLabel);
				if (parent == null || !(parent instanceof ModelNode) || (parent.getStatus() != Status.success && parent.getStatus() != Status.warning)) {
					BLogger.logConditional("Skipping model '" + modelLabel + "' for structure evaluation (not available or failed)");
					continue;
				}

				JSONObject entry = new JSONObject();
				entry.put("modelLabel", modelLabel);

				try {
					Path modelPath = ctx.modelPath(modelLabel);
					Path csvPath = outputDirPath.resolve(modelLabel + ".csv");
					if (!Files.exists(csvPath)) {
						CsvWriter.writeCsv(CmpxStructureExtractor.extract(Model.loadModel(modelPath.toString())), csvPath);
					}
					config.setFileOutputDagLearnedCsv(csvPath.getFileName().toString());
					evalConfigurer.apply().execute();

					entry.put("success", true);
					entry.put("bicScore", config.getCache().getBicScore());
					entry.put("logLikelihoodScore", config.getCache().getLogLikelihoodScore());
					entry.put("complexityScore", config.getCache().getComplexityScore());
					entry.put("freeParameters", config.getCache().getFreeParameters());
					anySuccess = true;
				}
				catch (Exception ex) {
					anyFailure = true;
					entry.put("success", false);
					entry.put("message", friendlyMessage(ex));
					BLogger.logConditional("Structure evaluation failed for '" + modelLabel + "': " + ex.getMessage());
					BLogger.logThrowableIfDebug(ex);
				}
				results.put(entry);
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
				setStatusMessage("Some model evaluations failed");
			}
			else {
				failWith("All model evaluations failed", null);
			}
		}
		catch (Exception ex) {
			failWith("Structure evaluation failed: " + friendlyMessage(ex), ex);
		}
	}

	@Override
	protected List<List<Object>> toCsvRows(JSONArray results) {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(Arrays.asList("modelLabel", "success", "bicScore", "logLikelihoodScore", "complexityScore", "freeParameters"));
		for (int i = 0; i < results.length(); i++) {
			JSONObject entry = results.optJSONObject(i);
			if (entry == null) continue;
			rows.add(Arrays.asList(
				entry.optString("modelLabel"),
				entry.optBoolean("success"),
				entry.opt("bicScore"),
				entry.opt("logLikelihoodScore"),
				entry.opt("complexityScore"),
				entry.opt("freeParameters")
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

	@Override
	public List<Path> getOutputFiles(GraphExecutionContext ctx) {
		List<Path> files = new ArrayList<>(super.getOutputFiles(ctx));
		for (String modelLabel : models) {
			files.add(ctx.getOutputDirPath().resolve(modelLabel + ".csv"));
		}
		return files;
	}
}
