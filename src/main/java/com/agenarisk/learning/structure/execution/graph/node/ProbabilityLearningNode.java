package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.TableLearningConfigurer;
import com.agenarisk.learning.structure.config.TableLearningExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONObject;

public class ProbabilityLearningNode extends ModelNode {

	private String model;
	private String dataSource;
	private int maxIterations = 50;
	private double convergenceThreshold = 0.01;
	private String missingValue = "";
	private String valueSeparator = ",";
	private double dataWeight = 1;
	private JSONObject jOptions;

	@Override
	public String getSubType() {
		return "probabilityLearning";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.jOptions = jOptions;
		this.model = jOptions.optString("model", "");
		this.dataSource = jOptions.optString("dataSource", "");
		this.maxIterations = jOptions.optInt("maxIterations", 50);
		this.convergenceThreshold = jOptions.optDouble("convergenceThreshold", 0.01);
		this.missingValue = jOptions.optString("missingValue", "");
		this.valueSeparator = jOptions.optString("valueSeparator", ",");
		this.dataWeight = jOptions.optDouble("dataWeight", 1);
	}

	@Override
	public Set<String> getInputLabels() {
		Set<String> labels = new LinkedHashSet<>();
		if (model != null && !model.isEmpty()) {
			labels.add(model);
		}
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

			Path inputModelPath = ctx.modelPath(model);
			Path outputModelPath = getModelPath(ctx);

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			config.setPathInput(dataPath.getParent().toString());
			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());

			TableLearningConfigurer configurer = new TableLearningConfigurer(config);

			// Build config JSON to reuse existing configureFromJson logic for knowledge/weights
			JSONObject jConfig = new JSONObject();
			JSONObject jParams = new JSONObject();
			jParams.put("maxIterations", maxIterations);
			jParams.put("convergenceThreshold", convergenceThreshold);
			jParams.put("missingValue", missingValue);
			jParams.put("valueSeparator", valueSeparator);
			jParams.put("dataWeight", dataWeight);
			jParams.put("dataPath", dataPath.toString());
			jParams.put("modelStageLabel", model);
			jConfig.put("parameters", jParams);
			// Forward knowledge if present in original options
			JSONObject jKnowledge = jOptions.optJSONObject("knowledge");
			if (jKnowledge != null) {
				jConfig.put("knowledge", jKnowledge);
			}
			configurer.configureFromJson(jConfig);

			// Override model-related fields after configureFromJson
			configurer.setModelStageLabel(model);
			configurer.setModelPrefix(model);
			configurer.setModelPath(outputModelPath);

			Model loadedModel = Model.loadModel(inputModelPath.toString());
			configurer.setModel(loadedModel);

			TableLearningExecutor executor = configurer.apply();
			executor.execute();

			Model resultModel = configurer.getModel();
			setResult(resultModel.toJson().optJSONObject("model"));
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Probability learning failed: " + friendlyMessage(ex), ex);
		}
	}

	public String getModel() {
		return model;
	}

	public String getDataSource() {
		return dataSource;
	}
}
