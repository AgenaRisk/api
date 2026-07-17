package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.LogisticRegressionTableLearningConfigurer;
import com.agenarisk.learning.structure.config.LogisticRegressionTableLearningExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONObject;

/**
 * Graph node for regression-based table learning that additionally covers categorical targets with continuous
 * parent(s), alongside {@link RegressionTableLearningNode} (which skips that case) and {@link ProbabilityLearningNode}
 * (EM-based) - selectable and comparable, not a replacement for either. See
 * {@code LogisticRegressionTableLearningConfigurer}/{@code LogisticRegressionTableLearningExecutor} for the actual
 * learning logic.
 *
 * @author Eugene Dementiev
 */
public class LogisticRegressionTableLearningNode extends ModelNode {

	private String model;
	private String dataSource;
	private String missingValue = "";
	private String valueSeparator = ",";
	private String residualMode = LogisticRegressionTableLearningConfigurer.RESIDUAL_MODE_NORMAL;
	private int minRowsPerPartition = 5;
	private double ridgeLambda = com.agenarisk.learning.structure.regression.MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA;
	private JSONObject jOptions;

	@Override
	public String getSubType() {
		return "logisticRegressionTableLearning";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.jOptions = jOptions;
		this.model = jOptions.optString("model", "");
		this.dataSource = jOptions.optString("dataSource", "");
		this.missingValue = jOptions.optString("missingValue", "");
		this.valueSeparator = jOptions.optString("valueSeparator", ",");
		this.residualMode = jOptions.optString("residualMode", LogisticRegressionTableLearningConfigurer.RESIDUAL_MODE_NORMAL);
		this.minRowsPerPartition = jOptions.optInt("minRowsPerPartition", 5);
		this.ridgeLambda = jOptions.optDouble("ridgeLambda", com.agenarisk.learning.structure.regression.MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA);
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

			LogisticRegressionTableLearningConfigurer configurer = new LogisticRegressionTableLearningConfigurer(config);

			JSONObject jConfig = new JSONObject();
			JSONObject jParams = new JSONObject();
			jParams.put("missingValue", missingValue);
			jParams.put("valueSeparator", valueSeparator);
			jParams.put("residualMode", residualMode);
			jParams.put("minRowsPerPartition", minRowsPerPartition);
			jParams.put("ridgeLambda", ridgeLambda);
			jParams.put("dataPath", dataPath.toString());
			jParams.put("modelStageLabel", model);
			jConfig.put("parameters", jParams);
			configurer.configureFromJson(jConfig);

			configurer.setModelStageLabel(model);
			configurer.setModelPrefix(model);
			configurer.setModelPath(outputModelPath);

			Model loadedModel = Model.loadModel(inputModelPath.toString());
			configurer.setModel(loadedModel);

			LogisticRegressionTableLearningExecutor executor = configurer.apply();
			executor.execute();

			Model resultModel = configurer.getModel();
			JSONObject jResult = resultModel.toJson().optJSONObject("model");
			JSONObject lastResult = executor.getLastResult();
			if (jResult != null && lastResult != null){
				jResult.put("logisticRegressionTableLearning", lastResult);
			}
			setResult(jResult);

			int skippedCount = 0;
			int totalCount = 0;
			StringBuilder reasons = new StringBuilder();
			if (lastResult != null){
				org.json.JSONArray jNodes = lastResult.optJSONArray("nodes");
				if (jNodes != null){
					totalCount = jNodes.length();
					for (int i = 0; i < jNodes.length(); i++){
						JSONObject jNode = jNodes.getJSONObject(i);
						if (jNode.optBoolean("skipped", false)){
							skippedCount++;
							if (reasons.length() > 0){
								reasons.append(" ");
							}
							reasons.append(jNode.optString("nodeId", "?")).append(": ").append(jNode.optString("reason", ""));
						}
					}
				}
			}

			if (skippedCount > 0){
				setStatus(Status.warning);
				setStatusMessage("Learned " + (totalCount - skippedCount) + " of " + totalCount + " node(s); "
						+ skippedCount + " skipped - " + reasons);
			}
			else {
				setStatus(Status.success);
			}
		}
		catch (Exception ex){
			failWith("Logistic regression table learning failed: " + friendlyMessage(ex), ex);
		}
	}

	public String getModel() {
		return model;
	}

	public String getDataSource() {
		return dataSource;
	}
}
