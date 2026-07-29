package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.AveragingConfigurer;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.utility.CmpxStructureExtractor;
import com.agenarisk.learning.structure.utility.ModelFromCsvCreator;
import com.agenarisk.learning.structure.utility.NodeStatesFromDataPopulator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModelAveragingNode extends ModelNode {

	private List<String> models = new ArrayList<>();
	private int keepLinksMin = 1;
	private boolean statesFromData = false;
	private String dataSource;

	@Override
	public String getSubType() {
		return "modelAveraging";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		JSONArray jModels = jOptions.optJSONArray("models");
		if (jModels != null) {
			for (int i = 0; i < jModels.length(); i++) {
				models.add(jModels.getString(i));
			}
		}
		keepLinksMin = Math.max(1, jOptions.optInt("keepLinksMin", 1));
		statesFromData = jOptions.optBoolean("statesFromData", false);
		dataSource = jOptions.optString("dataSource", "");
	}

	@Override
	public Set<String> getInputLabels() {
		Set<String> labels = new LinkedHashSet<>(models);
		if (statesFromData && dataSource != null && !dataSource.isEmpty()) {
			labels.add(dataSource);
		}
		return labels;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void execute(GraphExecutionContext ctx) {
		try {
			Path outputDirPath = ctx.getOutputDirPath();

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			config.setPathInput(outputDirPath.toString());
			config.setPathOutput(outputDirPath.toString());
			config.setAveragingMinimumEdgeAppearanceCountToKeep(keepLinksMin);

			List<List<Object>> lines = new ArrayList<>();
			lines.add(Arrays.asList("ID", "Variable 1", "Dependency", "Variable 2"));
			int avgCount = 0;
			int avgFailedCount = 0;

			for (String modelLabel : models) {
				GraphNode parent = ctx.getNode(modelLabel);
				if (parent == null || !(parent instanceof ModelNode) || (parent.getStatus() != Status.success && parent.getStatus() != Status.warning)) {
					BLogger.logConditional("Skipping model '" + modelLabel + "' for averaging (not available or failed)");
					continue;
				}
				try {
					Path modelPath = ctx.modelPath(modelLabel);
					Path csvPath = outputDirPath.resolve(modelLabel + ".csv");
					if (!Files.exists(csvPath)) {
						CsvWriter.writeCsv(CmpxStructureExtractor.extract(Model.loadModel(modelPath.toString())), csvPath);
					}
					lines.addAll(CmpxStructureExtractor.extract(Model.loadModel(modelPath.toString()), null));
					avgCount++;
				}
				catch (Exception ex) {
					avgFailedCount++;
					BLogger.logConditional("Failed to include model '" + modelLabel + "' in averaging: " + ex.getMessage());
					BLogger.logThrowableIfDebug(ex);
				}
			}

			if (avgCount == 0) {
				failWith("All models failed to process for averaging", null);
				return;
			}

			Path csvInput = outputDirPath.resolve(Config.FILE_AVERAGING_INPUT);
			CsvWriter.writeCsv(lines, csvInput);
			csvInput.toFile().deleteOnExit();

			AveragingConfigurer avgConfigurer = new AveragingConfigurer(config);
			avgConfigurer.apply().execute();

			Path csvOutput = outputDirPath.resolve(Config.FILE_AVERAGING_OUTPUT);
			csvOutput.toFile().deleteOnExit();

			Model model = ModelFromCsvCreator.create(CsvReader.readCsv(csvOutput), getLabel(), getLabel());

			if (statesFromData) {
				DataSourceNode dsNode = requireDataSource(ctx, dataSource);
				if (dsNode.getStatus() != Status.success && dsNode.getStatus() != Status.warning) {
					throw new StructureLearningException(
							"'" + getLabel() + "' has “states from data” enabled, but its data source '" + dataSource + "' did not run successfully.");
				}
				NodeStatesFromDataPopulator.populate(
								model.getNetworkList().get(0),
								dsNode.resolvedPath(ctx)
				);
			}

			Path modelPath = getModelPath(ctx);
			model.save(modelPath.toString());

			Files.copy(csvOutput, outputDirPath.resolve(getLabel() + ".csv"), StandardCopyOption.REPLACE_EXISTING);

			setResult(model.toJson().optJSONObject("model"));
			if (avgFailedCount > 0) {
				setStatus(Status.warning);
				setStatusMessage(avgFailedCount + " of " + (avgCount + avgFailedCount) + " models failed during averaging");
			}
			else {
				setStatus(Status.success);
			}
		}
		catch (Exception ex) {
			failWith("Averaging failed: " + friendlyMessage(ex), ex);
		}
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
		files.add(ctx.getOutputDirPath().resolve(getLabel() + ".csv"));
		return files;
	}
}
