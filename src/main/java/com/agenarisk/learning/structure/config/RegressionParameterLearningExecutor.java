package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.Advisory;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionNodeFitter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.util.EM.Data;

/**
 * Runs regression-based parameter learning for every node in the model's first network - the canonical, sole
 * regression-based parameter learner (see {@link RegressionParameterLearningConfigurer}). Delegates the actual
 * per-node fitting/writing/reporting to {@link RegressionNodeFitter}, the same helper
 * {@code RegressionModelMaterializer} uses to finalize a Regression Structure Discovery run - so this executor and
 * that materializer are provably doing the same fit and reporting the same diagnostic detail.
 *
 * @author Eugene Dementiev
 */
public class RegressionParameterLearningExecutor extends Configurer<RegressionParameterLearningExecutor> implements Executable {

	private RegressionParameterLearningConfigurer originalConfigurer;
	private JSONObject lastResult;

	protected RegressionParameterLearningExecutor(Config config) {
		super(config);
	}

	protected RegressionParameterLearningExecutor() {
		super();
	}

	public void setOriginalConfigurer(RegressionParameterLearningConfigurer originalConfigurer) {
		this.originalConfigurer = originalConfigurer;
	}

	/**
	 * @return the per-node JSON summary from the last {@link #execute()} call, or null if execute() hasn't run yet
	 */
	public JSONObject getLastResult() {
		return lastResult;
	}

	@Override
	public void execute() throws StructureLearningException {
		try {
			if (originalConfigurer == null){
				BLogger.logConditional("Original configurer not set");
				return;
			}

			Model model = originalConfigurer.getModel();
			Data data = new Data(originalConfigurer.getDataPath().toString(), originalConfigurer.getMissingValue(), originalConfigurer.getValueSeparator());

			Network network = model.getNetworkList().get(0);

			Map<String, List<String>> rankedStatesByNodeId = new HashMap<>();
			for (Node node : network.getNodeList()){
				if (node.getType() == Node.Type.Ranked){
					rankedStatesByNodeId.put(node.getId(), node.getStates().stream().map(com.agenarisk.api.model.State::getLabel).collect(Collectors.toList()));
				}
			}
			RegressionDataset dataset = new RegressionDataset(data, rankedStatesByNodeId);

			ContinuousRegressionLearner.ResidualMode residualMode = RegressionParameterLearningConfigurer.RESIDUAL_MODE_ARITHMETIC.equalsIgnoreCase(originalConfigurer.getResidualMode())
					? ContinuousRegressionLearner.ResidualMode.ARITHMETIC
					: ContinuousRegressionLearner.ResidualMode.NORMAL;

			ContinuousRegressionLearner continuousLearner = new ContinuousRegressionLearner(dataset, residualMode, originalConfigurer.getMinRowsPerPartition());
			CategoricalRegressionLearner categoricalLearner = new CategoricalRegressionLearner(dataset, originalConfigurer.getRidgeLambda());
			LogisticRegressionLearner logisticLearner = new LogisticRegressionLearner(dataset, originalConfigurer.getRidgeLambda());
			RegressionNodeFitter fitter = new RegressionNodeFitter(continuousLearner, categoricalLearner, logisticLearner);

			JSONArray jNodes = new JSONArray();

			List<Node> nodeList = network.getNodeList();
			long lastProgressEmitMs = System.currentTimeMillis();

			for (int nodeIndex = 0; nodeIndex < nodeList.size(); nodeIndex++){
				Node node = nodeList.get(nodeIndex);

				if (originalConfigurer.isProgressEnabled()){
					long nowMs = System.currentTimeMillis();
					if (nowMs - lastProgressEmitMs >= 1000 || nodeIndex == 0){
						GraphNode.emitProgress(originalConfigurer.getNodeLabel(),
								"Fitting node '" + node.getId() + "' (" + (nodeIndex + 1) + " of " + nodeList.size() + ")",
								nodeIndex + 1, nodeList.size());
						lastProgressEmitMs = nowMs;
					}
				}

				RegressionNodeFitter.NodeFitOutcome outcome = fitter.fitAndWrite(node);

				JSONObject jNode = new JSONObject();
				jNode.put("nodeId", outcome.getNodeId());
				jNode.put("skipped", outcome.isSkipped());

				if (outcome.isSkipped()){
					reportSkip(jNode, outcome.getSkipReason());
				}
				else if (outcome.getDetail() != null){
					for (String key : outcome.getDetail().keySet()){
						jNode.put(key, outcome.getDetail().get(key));
					}
				}
				jNodes.put(jNode);
			}

			lastResult = new JSONObject().put("nodes", jNodes);

			byte[] bytes = model.export(Model.ExportFlag.KEEP_META, Model.ExportFlag.KEEP_OBSERVATIONS, Model.ExportFlag.KEEP_RESULTS).toString().getBytes();
			Files.write(originalConfigurer.getModelPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			originalConfigurer.setModel(model);
		}
		catch (Exception ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

	private void reportSkip(JSONObject jNode, String reason) {
		jNode.put("reason", reason);

		Advisory.AdvisoryGroup group = Advisory.getCurrentThreadGroup();
		if (group != null){
			group.addMessage(new Advisory.AdvisoryMessage(reason));
		}
		BLogger.logConditional(reason);
	}
}
