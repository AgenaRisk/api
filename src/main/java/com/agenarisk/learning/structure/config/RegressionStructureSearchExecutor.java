package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regressiondiscovery.CandidateGraph;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionBicScorer;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionModelMaterializer;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionNodeFitter;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionStructureResult;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionStructureSearch;
import com.agenarisk.learning.structure.regressiondiscovery.ShellModelBuilder;
import com.agenarisk.api.util.CsvReader;
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
 * Runs a {@link RegressionStructureSearch} over every node in the model's first network, then materializes the
 * winning structure (links + fitted tables) via {@link RegressionModelMaterializer}.
 * <br>
 * Reports its own BIC/log-likelihood/free-parameter numbers under the same field names the legacy discrete evaluator
 * uses ({@code bicScore}, {@code logLikelihoodScore}, {@code complexityScore}, {@code freeParameters}) so a
 * regression-discovery run and a legacy-discrete run can be compared side by side in reporting - not because the two
 * scorers are on the same numeric scale (they are not: this one is a regression-BIC over raw mixed data, the legacy
 * one is a discrete BIC over pre-discretized data).
 *
 * @author Eugene Dementiev
 */
public class RegressionStructureSearchExecutor extends Configurer<RegressionStructureSearchExecutor> implements Executable {

	private RegressionStructureConfigurer originalConfigurer;
	private JSONObject lastResult;

	protected RegressionStructureSearchExecutor(Config config) {
		super(config);
	}

	protected RegressionStructureSearchExecutor() {
		super();
	}

	public void setOriginalConfigurer(RegressionStructureConfigurer originalConfigurer) {
		this.originalConfigurer = originalConfigurer;
	}

	public JSONObject getLastResult() {
		return lastResult;
	}

	@Override
	public void execute() throws StructureLearningException {
		try {
			if (originalConfigurer == null){
				throw new StructureLearningException("Original configurer not set");
			}

			List<String> csvHeaders = CsvReader.readHeaders(originalConfigurer.getDataPath());
			Model model = ShellModelBuilder.build(csvHeaders, originalConfigurer.getDataPath(),
					originalConfigurer.getVariableDeclarations(), originalConfigurer.getMissingValue());
			Data data = new Data(originalConfigurer.getDataPath().toString(), originalConfigurer.getMissingValue(), originalConfigurer.getValueSeparator());

			Network network = model.getNetworkList().get(0);

			Map<String, List<String>> rankedStatesByNodeId = new HashMap<>();
			Map<String, Node> nodesById = new HashMap<>();
			for (Node node : network.getNodeList()){
				nodesById.put(node.getId(), node);
				if (node.getType() == Node.Type.Ranked){
					rankedStatesByNodeId.put(node.getId(), node.getStates().stream().map(com.agenarisk.api.model.State::getLabel).collect(Collectors.toList()));
				}
			}
			RegressionDataset dataset = new RegressionDataset(data, rankedStatesByNodeId);

			RegressionBicScorer scorer = new RegressionBicScorer(dataset, originalConfigurer.getRidgeLambda());
			RegressionStructureSearch search = new RegressionStructureSearch(scorer, originalConfigurer.getKnowledge(),
					originalConfigurer.getMaxParentsPerNode(), originalConfigurer.getMaxIterations());
			if (originalConfigurer.isProgressEnabled()){
				search.enableProgressReporting(originalConfigurer.getNodeLabel());
			}
			RegressionStructureResult result = search.search(nodesById);

			ContinuousRegressionLearner continuousLearner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.NORMAL);
			CategoricalRegressionLearner categoricalLearner = new CategoricalRegressionLearner(dataset, originalConfigurer.getRidgeLambda());
			LogisticRegressionLearner logisticLearner = new LogisticRegressionLearner(dataset, originalConfigurer.getRidgeLambda());
			RegressionNodeFitter fitter = new RegressionNodeFitter(continuousLearner, categoricalLearner, logisticLearner);

			List<RegressionNodeFitter.NodeFitOutcome> outcomes = RegressionModelMaterializer.materialize(model, result, fitter);

			lastResult = buildResultJson(result, outcomes);

			byte[] bytes = model.export(Model.ExportFlag.KEEP_META, Model.ExportFlag.KEEP_OBSERVATIONS, Model.ExportFlag.KEEP_RESULTS).toString().getBytes();
			Files.write(originalConfigurer.getModelPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			originalConfigurer.setModel(model);
		}
		catch (StructureLearningException ex){
			throw ex;
		}
		catch (Exception ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

	private JSONObject buildResultJson(RegressionStructureResult result, List<RegressionNodeFitter.NodeFitOutcome> outcomes) {

		JSONObject jResult = new JSONObject();
		jResult.put("bicScore", result.getTotalBic());
		jResult.put("logLikelihoodScore", result.getTotalLogLikelihood());
		jResult.put("freeParameters", result.getTotalFreeParameters());
		double complexityScore = 0;
		for (com.agenarisk.learning.structure.regressiondiscovery.LocalScore score : result.getLocalScoresByNodeId().values()){
			complexityScore += score.getFreeParameterCount() * Math.log(Math.max(score.getN(), 1));
		}
		jResult.put("complexityScore", complexityScore);
		jResult.put("iterations", result.getIterations());
		jResult.put("iterationCapReached", result.isIterationCapReached());

		CandidateGraph graph = result.getGraph();
		JSONArray jEdges = new JSONArray();
		for (String childId : graph.getNodeIds()){
			for (String parentId : graph.getParents(childId)){
				JSONObject jEdge = new JSONObject();
				jEdge.put("parent", parentId);
				jEdge.put("child", childId);
				jEdges.put(jEdge);
			}
		}
		jResult.put("edges", jEdges);

		JSONArray jNodes = new JSONArray();
		for (RegressionNodeFitter.NodeFitOutcome outcome : outcomes){
			JSONObject jNode = new JSONObject();
			jNode.put("nodeId", outcome.getNodeId());
			jNode.put("skipped", outcome.isSkipped());
			if (outcome.isSkipped()){
				jNode.put("reason", outcome.getSkipReason());
			}
			com.agenarisk.learning.structure.regressiondiscovery.LocalScore score = result.getLocalScore(outcome.getNodeId());
			if (score != null){
				jNode.put("bic", score.getBic());
				jNode.put("logLikelihood", score.getLogLikelihood());
				jNode.put("freeParameters", score.getFreeParameterCount());
				jNode.put("n", score.getN());
			}
			if (outcome.getDetail() != null){
				for (String key : outcome.getDetail().keySet()){
					jNode.put(key, outcome.getDetail().get(key));
				}
			}
			jNodes.put(jNode);
		}
		jResult.put("nodes", jNodes);

		return jResult;
	}
}
