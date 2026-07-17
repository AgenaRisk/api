package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.Advisory;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.CategoricalTableWriter;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.LogisticExpressionTableWriter;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.NodeRole;
import com.agenarisk.learning.structure.regression.PartitionEnumerator;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regression.RegressionTableWriter;
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
 * Runs regression-based table learning for every node in the model's first network, extending
 * {@link RegressionTableLearningExecutor} with one additional case it doesn't cover: a categorical node with a
 * continuous parent.
 * <br>
 * For each node:
 * <ul>
 * <li>Continuous nodes are fitted via {@link ContinuousRegressionLearner} and written back with
 * {@link RegressionTableWriter} - identical to {@code RegressionTableLearningExecutor}.</li>
 * <li>Categorical nodes with only categorical parents are fitted via {@link CategoricalRegressionLearner} and
 * written back as a manual NPT with {@link CategoricalTableWriter} - identical to
 * {@code RegressionTableLearningExecutor}.</li>
 * <li>Categorical nodes with at least one continuous parent are fitted via {@link LogisticRegressionLearner} and
 * written back as a single persisted {@code MultinomialLogit(...)} expression with
 * {@link LogisticExpressionTableWriter} - this is the case {@code RegressionTableLearningExecutor} skips.</li>
 * </ul>
 *
 * @author Eugene Dementiev
 */
public class LogisticRegressionTableLearningExecutor extends Configurer<LogisticRegressionTableLearningExecutor> implements Executable {

	private LogisticRegressionTableLearningConfigurer originalConfigurer;
	private JSONObject lastResult;

	protected LogisticRegressionTableLearningExecutor(Config config) {
		super(config);
	}

	protected LogisticRegressionTableLearningExecutor() {
		super();
	}

	public void setOriginalConfigurer(LogisticRegressionTableLearningConfigurer originalConfigurer) {
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

			ContinuousRegressionLearner.ResidualMode residualMode = LogisticRegressionTableLearningConfigurer.RESIDUAL_MODE_ARITHMETIC.equalsIgnoreCase(originalConfigurer.getResidualMode())
					? ContinuousRegressionLearner.ResidualMode.ARITHMETIC
					: ContinuousRegressionLearner.ResidualMode.NORMAL;

			ContinuousRegressionLearner continuousLearner = new ContinuousRegressionLearner(dataset, residualMode, originalConfigurer.getMinRowsPerPartition());
			CategoricalRegressionLearner categoricalLearner = new CategoricalRegressionLearner(dataset, originalConfigurer.getRidgeLambda());
			LogisticRegressionLearner logisticLearner = new LogisticRegressionLearner(dataset, originalConfigurer.getRidgeLambda());

			JSONArray jNodes = new JSONArray();

			for (Node node : network.getNodeList()){
				JSONObject jNode = new JSONObject();
				jNode.put("nodeId", node.getId());

				if (NodeRole.of(node) == NodeRole.CATEGORICAL){
					boolean hasContinuousParent = node.getParents().stream().anyMatch(parent -> NodeRole.of(parent) == NodeRole.CONTINUOUS);

					if (hasContinuousParent){
						LogisticRegressionLearner.NodeLearningResult result = logisticLearner.learn(node);
						if (result.isSkipped()){
							reportSkip(jNode, result.getSkipReason());
							jNodes.put(jNode);
							continue;
						}

						LogisticExpressionTableWriter.apply(result);

						jNode.put("skipped", false);
						jNode.put("n", result.getN());
						jNode.put("pseudoR2", Double.isNaN(result.getPseudoR2()) ? JSONObject.NULL : result.getPseudoR2());
						jNode.put("converged", result.isConverged());
						jNode.put("expression", result.getExpression());
						jNodes.put(jNode);
						continue;
					}

					CategoricalRegressionLearner.NodeLearningResult result = categoricalLearner.learn(node);
					if (result.isSkipped()){
						reportSkip(jNode, result.getSkipReason());
						jNodes.put(jNode);
						continue;
					}

					CategoricalTableWriter.apply(result);

					jNode.put("skipped", false);
					jNode.put("n", result.getN());
					jNode.put("pseudoR2", Double.isNaN(result.getPseudoR2()) ? JSONObject.NULL : result.getPseudoR2());
					jNode.put("converged", result.isConverged());

					List<PartitionEnumerator.Combination> combinations = PartitionEnumerator.enumerate(result.getParents());
					List<String> targetStates = node.getStates().stream().map(com.agenarisk.api.model.State::getLabel).collect(Collectors.toList());
					JSONArray jCombinations = new JSONArray();
					for (int c = 0; c < combinations.size(); c++){
						JSONObject jCombination = new JSONObject();
						jCombination.put("states", combinations.get(c).getStatesByNodeId());
						JSONObject jProbabilities = new JSONObject();
						for (int s = 0; s < targetStates.size(); s++){
							jProbabilities.put(targetStates.get(s), result.getNpt()[c][s]);
						}
						jCombination.put("probabilities", jProbabilities);
						jCombinations.put(jCombination);
					}
					jNode.put("combinations", jCombinations);
					jNodes.put(jNode);
					continue;
				}

				ContinuousRegressionLearner.NodeLearningResult result = continuousLearner.learn(node);
				if (result.isSkipped()){
					reportSkip(jNode, result.getSkipReason());
					jNodes.put(jNode);
					continue;
				}

				RegressionTableWriter.apply(result);

				jNode.put("skipped", false);
				JSONArray jPartitions = new JSONArray();
				for (ContinuousRegressionLearner.PartitionResult pr : result.getPartitionResults()){
					JSONObject jPartition = new JSONObject();
					if (pr.getCombination() != null){
						jPartition.put("states", pr.getCombination().getStatesByNodeId());
					}
					jPartition.put("n", pr.getN());
					jPartition.put("r2", Double.isNaN(pr.getR2()) ? JSONObject.NULL : pr.getR2());
					jPartition.put("fitSource", pr.getFitSource().name());
					jPartitions.put(jPartition);
				}
				jNode.put("partitions", jPartitions);
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
		jNode.put("skipped", true);
		jNode.put("reason", reason);

		Advisory.AdvisoryGroup group = Advisory.getCurrentThreadGroup();
		if (group != null){
			group.addMessage(new Advisory.AdvisoryMessage(reason));
		}
		BLogger.logConditional(reason);
	}
}
