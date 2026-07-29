package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.GenerationConfigurer;
import com.agenarisk.learning.structure.config.GenerationExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import BNlearning.Database;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONObject;

public class ModelGenerationNode extends ModelNode {

	private String dataSource;
	private int maximumEdgeCount = 2;
	private boolean statesFromData = false;

	@Override
	public String getSubType() {
		return "modelGeneration";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.dataSource = jOptions.optString("dataSource", "");
		this.maximumEdgeCount = jOptions.optInt("maximumEdgeCount", 2);
		this.statesFromData = jOptions.optBoolean("statesFromData", false);
	}

	@Override
	public Set<String> getInputLabels() {
		Set<String> labels = new LinkedHashSet<>();
		if (dataSource != null && !dataSource.isEmpty()) {
			labels.add(dataSource);
		}
		return labels;
	}

	@Override
	public void execute(GraphExecutionContext ctx) {
		try {
			DataSourceNode dsNode = requireDataSource(ctx, dataSource);
			Path dataPath = dsNode.resolvedPath(ctx);
			Path modelPath = getModelPath(ctx);

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			config.setPathInput(dataPath.getParent().toString());
			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());

			GenerationConfigurer configurer = new GenerationConfigurer(config);
			JSONObject jParams = new JSONObject();
			jParams.put("maximumEdgeCount", maximumEdgeCount);
			jParams.put("statesFromData", statesFromData);
			JSONObject jConfig = new JSONObject();
			jConfig.put("parameters", jParams);
			configurer.configureFromJson(jConfig);
			configurer.setModelPath(modelPath);

			GenerationExecutor executor = configurer.apply();
			executor.execute();

			Model model = configurer.getModel();
			setResult(model.toJson().optJSONObject("model"));
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Failed to generate model: " + friendlyMessage(ex), ex);
		}
	}

	public String getDataSource() {
		return dataSource;
	}
}
