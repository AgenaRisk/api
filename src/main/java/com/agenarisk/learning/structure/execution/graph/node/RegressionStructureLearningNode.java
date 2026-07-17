package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.RegressionStructureConfigurer;
import com.agenarisk.learning.structure.config.RegressionStructureSearchExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionKnowledge;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONObject;

/**
 * Graph node for Regression Structure Discovery - a single new, self-contained greedy search over DAGs scored with a
 * decomposable regression-based BIC over mixed continuous/discrete data, selectable alongside (never replacing) the
 * legacy discrete-BIC discovery algorithms ({@code modelDiscovery} node type / HC/Tabu/GES/SaiyanH/MAHC).
 * <br>
 * Inputs: {@code model} - a "shell" model declaring every candidate variable's type/states/bounds (its links, if
 * any, are irrelevant - discovering them is this node's job); {@code dataSource} - raw, non-discretized CSV data.
 *
 * @author Eugene Dementiev
 */
public class RegressionStructureLearningNode extends ModelNode {

	private String model;
	private String dataSource;
	private String missingValue = "";
	private String valueSeparator = ",";
	private double ridgeLambda = com.agenarisk.learning.structure.regression.MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA;
	private int maxParentsPerNode = 5;
	private int maxIterations = 500;
	private JSONObject jOptions;

	@Override
	public String getSubType() {
		return "regressionStructureDiscovery";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.jOptions = jOptions;
		this.model = jOptions.optString("model", "");
		this.dataSource = jOptions.optString("dataSource", "");
		this.missingValue = jOptions.optString("missingValue", "");
		this.valueSeparator = jOptions.optString("valueSeparator", ",");
		this.ridgeLambda = jOptions.optDouble("ridgeLambda", com.agenarisk.learning.structure.regression.MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA);
		this.maxParentsPerNode = jOptions.optInt("maxParentsPerNode", 5);
		this.maxIterations = jOptions.optInt("maxIterations", 500);
	}

	@Override
	public Set<String> getInputLabels() {
		Set<String> labels = new LinkedHashSet<>();
		if (model != null && !model.isEmpty()){
			labels.add(model);
		}
		if (dataSource != null && !dataSource.isEmpty()){
			labels.add(dataSource);
		}
		return labels;
	}

	@Override
	public void execute(GraphExecutionContext ctx) {
		try {
			DataSourceNode dsNode = requireDataSource(ctx, dataSource);
			Path dataPath = dsNode.resolvedPath(ctx);

			requireModelInput(ctx, model);
			Path inputModelPath = ctx.modelPath(model);
			Path outputModelPath = getModelPath(ctx);

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			config.setPathInput(dataPath.getParent().toString());
			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());

			RegressionStructureConfigurer configurer = new RegressionStructureConfigurer(config);

			JSONObject jConfig = new JSONObject();
			JSONObject jParams = new JSONObject();
			jParams.put("missingValue", missingValue);
			jParams.put("valueSeparator", valueSeparator);
			jParams.put("ridgeLambda", ridgeLambda);
			jParams.put("maxParentsPerNode", maxParentsPerNode);
			jParams.put("maxIterations", maxIterations);
			jParams.put("dataPath", dataPath.toString());
			jParams.put("modelStageLabel", model);
			jConfig.put("parameters", jParams);
			if (jOptions != null && jOptions.has("knowledge")){
				jConfig.put("knowledge", jOptions.getJSONObject("knowledge"));
			}
			configurer.configureFromJson(jConfig);

			configurer.setModelStageLabel(model);
			configurer.setModelPrefix(model);
			configurer.setModelPath(outputModelPath);

			Model loadedModel = Model.loadModel(inputModelPath.toString());
			configurer.setModel(loadedModel);

			RegressionStructureSearchExecutor executor = configurer.apply();
			executor.execute();

			Model resultModel = configurer.getModel();
			JSONObject jResult = resultModel.toJson().optJSONObject("model");
			JSONObject lastResult = executor.getLastResult();
			if (jResult != null && lastResult != null){
				jResult.put("regressionStructureDiscovery", lastResult);
			}
			setResult(jResult);

			if (lastResult != null && lastResult.optBoolean("iterationCapReached", false)){
				setStatus(Status.warning);
				setStatusMessage("Search stopped at the iteration cap (" + maxIterations + ") rather than reaching a confirmed local optimum");
			}
			else {
				setStatus(Status.success);
			}
		}
		catch (Exception ex){
			failWith("Regression structure discovery failed: " + friendlyMessage(ex), ex);
		}
	}

	public String getModel() {
		return model;
	}

	public String getDataSource() {
		return dataSource;
	}
}
