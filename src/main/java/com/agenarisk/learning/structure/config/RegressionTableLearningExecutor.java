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
import com.agenarisk.learning.structure.regression.NodeRole;
import com.agenarisk.learning.structure.regression.PartitionEnumerator;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regression.RegressionEligibility;
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
 * Runs OLS/regression-based table learning for every eligible node in the model's first network.
 * <br>
 * For each node:
 * <ul>
 * <li>Continuous nodes (ContinuousInterval, IntegerInterval, Ranked) are fitted via {@link ContinuousRegressionLearner}
 * and written back with {@link RegressionTableWriter}.</li>
 * <li>Categorical nodes (Boolean, Labelled, DiscreteReal) with only categorical parents are fitted via
 * {@link CategoricalRegressionLearner} (ridge-regularized multinomial logistic regression) and written back as a
 * manual NPT with {@link CategoricalTableWriter}.</li>
 * <li>Categorical nodes with a continuous parent are flagged and skipped - see {@link RegressionEligibility} for
 * why.</li>
 * </ul>
 * Skips are reported both as {@link Advisory} messages (when the current thread is linked to an AdvisoryGroup) and
 * in the JSON result, so a skip is never silent.
 *
 * @author Eugene Dementiev
 */
public class RegressionTableLearningExecutor extends Configurer<RegressionTableLearningExecutor> implements Executable {

	private RegressionTableLearningConfigurer originalConfigurer;
	private JSONObject lastResult;

	protected RegressionTableLearningExecutor(Config config) {
		super(config);
	}

	protected RegressionTableLearningExecutor() {
		super();
	}

	public void setOriginalConfigurer(RegressionTableLearningConfigurer originalConfigurer) {
		this.originalConfigurer = originalConfigurer;
	}

	/**
	 * @return the per-node JSON summary from the last {@link #execute()} call (node ids, skip reasons, and per-partition N/R2), or null if execute() hasn't run yet
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

			// A Ranked node's raw CSV value is normally its state label ("Low"/"Medium"/"High"), not a number -
			// without this mapping, RegressionDataset's numeric parsing fails for every row of a Ranked column,
			// silently producing zero usable rows and a degenerate fit (see the reported "Fuel_price"-style bug,
			// but for Ranked roots like car_type/Reliability collapsing to ~100% on their first state).
			Map<String, List<String>> rankedStatesByNodeId = new HashMap<>();
			for (Node node : network.getNodeList()){
				if (node.getType() == Node.Type.Ranked){
					rankedStatesByNodeId.put(node.getId(), node.getStates().stream().map(com.agenarisk.api.model.State::getLabel).collect(Collectors.toList()));
				}
			}
			RegressionDataset dataset = new RegressionDataset(data, rankedStatesByNodeId);

			ContinuousRegressionLearner.ResidualMode residualMode = RegressionTableLearningConfigurer.RESIDUAL_MODE_ARITHMETIC.equalsIgnoreCase(originalConfigurer.getResidualMode())
					? ContinuousRegressionLearner.ResidualMode.ARITHMETIC
					: ContinuousRegressionLearner.ResidualMode.NORMAL;

			ContinuousRegressionLearner continuousLearner = new ContinuousRegressionLearner(dataset, residualMode, originalConfigurer.getMinRowsPerPartition());
			CategoricalRegressionLearner categoricalLearner = new CategoricalRegressionLearner(dataset, originalConfigurer.getRidgeLambda());

			JSONArray jNodes = new JSONArray();

			for (Node node : network.getNodeList()){
				JSONObject jNode = new JSONObject();
				jNode.put("nodeId", node.getId());

				if (NodeRole.of(node) == NodeRole.CATEGORICAL){
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
