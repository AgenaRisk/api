package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.RegressionStructureConfigurer;
import com.agenarisk.learning.structure.config.RegressionStructureSearchExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONObject;

/**
 * Graph node for Regression Structure Discovery - a single new, self-contained greedy search over DAGs scored with a
 * decomposable regression-based BIC over mixed continuous/discrete data, selectable alongside (never replacing) the
 * legacy discrete-BIC discovery algorithms ({@code discreteStructureDiscovery} node type / HC/Tabu/GES/SaiyanH/MAHC).
 * <br>
 * This node *produces* a model from data - like {@code discreteStructureDiscovery}/{@code modelGeneration}, it takes only a
 * {@code dataSource}, no model input at all. Each column's node type/states is either declared explicitly via the
 * {@code variables} option (parsed by {@link RegressionStructureConfigurer#configureFromJson}, one
 * {@code com.agenarisk.learning.structure.regressiondiscovery.VariableDeclaration} per column) or defaulted
 * (numeric -> simulated ContinuousInterval, non-numeric -> Labelled with auto-detected states) by
 * {@link com.agenarisk.learning.structure.regressiondiscovery.ShellModelBuilder}, which
 * {@link RegressionStructureSearchExecutor} calls internally - there is nowhere in this node that loads an existing
 * {@code .cmpx} file.
 *
 * @author Eugene Dementiev
 */
public class RegressionStructureDiscoveryNode extends ModelNode {

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
			// Relations the caller has established hold exactly. Forwarded explicitly because this block is
			// assembled key by key - an option absent from it is dropped in silence, which is how a declared
			// deterministic expression reached the configurer as an empty map and every node was fitted anyway.
			if (jOptions != null && jOptions.has("deterministicExpressions")){
				jParams.put("deterministicExpressions", jOptions.getJSONObject("deterministicExpressions"));
			}
			jConfig.put("parameters", jParams);
			if (jOptions != null && jOptions.has("knowledge")){
				jConfig.put("knowledge", jOptions.getJSONObject("knowledge"));
			}
			if (jOptions != null && jOptions.has("variables")){
				jConfig.put("variables", jOptions.getJSONObject("variables"));
			}
			configurer.configureFromJson(jConfig);
			configurer.setModelPath(outputModelPath);
			configurer.setNodeLabel(getLabel());
			configurer.setProgressEnabled(ctx.isProgressOutput());

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

	public String getDataSource() {
		return dataSource;
	}
}
