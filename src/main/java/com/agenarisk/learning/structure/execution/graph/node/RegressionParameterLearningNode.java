package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.RegressionParameterLearningConfigurer;
import com.agenarisk.learning.structure.config.RegressionParameterLearningExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONObject;

/**
 * Graph node for regression-based parameter learning: fits every node's table against its already-fixed parents
 * (continuous targets via OLS, categorical targets with only categorical parents via ridge-regularized multinomial
 * logistic regression baked to a manual NPT, categorical targets with any continuous parent via a persisted
 * {@code MultinomialLogit(...)} expression) - the canonical, sole regression-based parameter learner, alongside
 * {@link EMParameterLearningNode} (EM-based) and {@link RegressionStructureDiscoveryNode} (structure + parameters
 * together). See {@code RegressionParameterLearningConfigurer}/{@code RegressionParameterLearningExecutor} for the
 * actual learning logic.
 *
 * @author Eugene Dementiev
 */
public class RegressionParameterLearningNode extends ModelNode {

	private String model;
	private String dataSource;
	private String missingValue = "";
	private String valueSeparator = ",";
	private String residualMode = RegressionParameterLearningConfigurer.RESIDUAL_MODE_NORMAL;
	private int minRowsPerPartition = 5;
	private double ridgeLambda = com.agenarisk.learning.structure.regression.MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA;
	private JSONObject jOptions;

	@Override
	public String getSubType() {
		return "regressionParameterLearning";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.jOptions = jOptions;
		this.model = jOptions.optString("model", "");
		this.dataSource = jOptions.optString("dataSource", "");
		this.missingValue = jOptions.optString("missingValue", "");
		this.valueSeparator = jOptions.optString("valueSeparator", ",");
		this.residualMode = jOptions.optString("residualMode", RegressionParameterLearningConfigurer.RESIDUAL_MODE_NORMAL);
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

			RegressionParameterLearningConfigurer configurer = new RegressionParameterLearningConfigurer(config);

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
			configurer.setNodeLabel(getLabel());
			configurer.setProgressEnabled(ctx.isProgressOutput());

			Model loadedModel = Model.loadModel(inputModelPath.toString());
			configurer.setModel(loadedModel);

			RegressionParameterLearningExecutor executor = configurer.apply();
			executor.execute();

			Model resultModel = configurer.getModel();
			JSONObject jResult = resultModel.toJson().optJSONObject("model");
			JSONObject lastResult = executor.getLastResult();
			if (jResult != null && lastResult != null){
				jResult.put("regressionParameterLearning", lastResult);
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
			failWith("Regression parameter learning failed: " + friendlyMessage(ex), ex);
		}
	}

	public String getModel() {
		return model;
	}

	public String getDataSource() {
		return dataSource;
	}
}
