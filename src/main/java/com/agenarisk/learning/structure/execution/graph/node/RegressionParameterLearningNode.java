package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionNodeFitter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.util.EM.Data;

/**
 * A third parameter-learning path, alongside {@link ProbabilityLearningNode} (EM-based) and
 * {@link RegressionTableLearningNode}/{@link LogisticRegressionTableLearningNode} (bake-to-manual-table OLS/logit):
 * fits and persists native regression expressions (continuous targets get {@code Normal}/{@code TNormal}/
 * {@code Arithmetic} expressions, categorical targets get a manual NPT or a {@code MultinomialLogit} expression
 * depending on whether they have a continuous parent) against a structure that's already fixed - e.g. an imported
 * model whose links were set by hand or by {@link RegressionStructureLearningNode} in an earlier stage.
 * <br>
 * Uses the exact same {@link RegressionNodeFitter} helper {@link RegressionStructureLearningNode}'s materializer
 * uses, so "search discovers structure" and "this node bakes tables for fixed structure" are provably doing the same
 * fit - just over a different parent-set source (search-chosen vs. already-in-model).
 *
 * @author Eugene Dementiev
 */
public class RegressionParameterLearningNode extends ModelNode {

	private String model;
	private String dataSource;
	private String missingValue = "";
	private String valueSeparator = ",";
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

			Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			Model loadedModel = Model.loadModel(inputModelPath.toString());
			Data data = new Data(dataPath.toString(), missingValue, valueSeparator);

			Network network = loadedModel.getNetworkList().get(0);
			Map<String, List<String>> rankedStatesByNodeId = new HashMap<>();
			for (Node node : network.getNodeList()){
				if (node.getType() == Node.Type.Ranked){
					rankedStatesByNodeId.put(node.getId(), node.getStates().stream().map(com.agenarisk.api.model.State::getLabel).collect(Collectors.toList()));
				}
			}
			RegressionDataset dataset = new RegressionDataset(data, rankedStatesByNodeId);

			ContinuousRegressionLearner continuousLearner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.NORMAL);
			CategoricalRegressionLearner categoricalLearner = new CategoricalRegressionLearner(dataset, ridgeLambda);
			LogisticRegressionLearner logisticLearner = new LogisticRegressionLearner(dataset, ridgeLambda);
			RegressionNodeFitter fitter = new RegressionNodeFitter(continuousLearner, categoricalLearner, logisticLearner);

			JSONArray jNodes = new JSONArray();
			int skippedCount = 0;
			int totalCount = 0;
			StringBuilder reasons = new StringBuilder();

			for (Node node : network.getNodeList()){
				RegressionNodeFitter.NodeFitOutcome outcome = fitter.fitAndWrite(node);
				totalCount++;
				JSONObject jNode = new JSONObject();
				jNode.put("nodeId", outcome.getNodeId());
				jNode.put("skipped", outcome.isSkipped());
				if (outcome.isSkipped()){
					skippedCount++;
					jNode.put("reason", outcome.getSkipReason());
					if (reasons.length() > 0){
						reasons.append(" ");
					}
					reasons.append(outcome.getNodeId()).append(": ").append(outcome.getSkipReason());
				}
				jNodes.put(jNode);
			}

			JSONObject lastResult = new JSONObject().put("nodes", jNodes);

			byte[] bytes = loadedModel.export(Model.ExportFlag.KEEP_META, Model.ExportFlag.KEEP_OBSERVATIONS, Model.ExportFlag.KEEP_RESULTS).toString().getBytes();
			Files.write(outputModelPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			JSONObject jResult = loadedModel.toJson().optJSONObject("model");
			if (jResult != null){
				jResult.put("regressionParameterLearning", lastResult);
			}
			setResult(jResult);

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
