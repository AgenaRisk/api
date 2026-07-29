package com.agenarisk.test.learning;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regressiondiscovery.CandidateGraph;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionBicScorer;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionKnowledge;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionStructureResult;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionStructureSearch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.EM.Data;
import uk.co.agena.minerva.util.Environment;

public class RegressionStructureSearchTest {

	{
		Environment.initialize();
	}

	private Data writeAndLoadData(String csv) throws IOException {
		Path tempFile = Files.createTempFile("regression-structure-search-test-", ".csv");
		tempFile.toFile().deleteOnExit();
		Files.write(tempFile, csv.getBytes(StandardCharsets.UTF_8));
		try {
			return new Data(tempFile.toString(), "NA", ",");
		}
		catch (Exception ex){
			throw new RuntimeException(ex);
		}
	}

	private static final String SHELL_MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"x2\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": []"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testRecoversRelevantVariableAndIgnoresIrrelevantOne() throws Exception {
		// y = 2 + 3*x1 + moderate noise; x2 is pure noise, independent of both x1 and y.
		// NOTE: for a simple two-variable linear-Gaussian relationship, BIC alone cannot reliably identify the
		// causal DIRECTION between x1 and y (x1->y and y->x1 imply the same joint distribution up to fit quality,
		// the well-known Markov-equivalence limitation of score-based discovery) - so this test only asserts an
		// edge exists connecting the truly-related pair (in either direction), and that the unrelated variable x2
		// stays fully disconnected, which BIC search should always get right regardless of direction ambiguity.
		Random random = new Random(42);
		StringBuilder csv = new StringBuilder("x1,x2,y\n");
		for (int i = 0; i < 300; i++){
			double x1 = random.nextGaussian();
			double x2 = random.nextGaussian();
			double y = 2 + 3 * x1 + random.nextGaussian() * 0.5;
			csv.append(x1).append(",").append(x2).append(",").append(y).append("\n");
		}

		Model model = Model.createModel(new JSONObject(SHELL_MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Map<String, Node> nodesById = new HashMap<>();
		for (Node node : network.getNodeList()){
			nodesById.put(node.getId(), node);
		}

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		RegressionBicScorer scorer = new RegressionBicScorer(dataset);
		RegressionStructureSearch search = new RegressionStructureSearch(scorer, new RegressionKnowledge(), 5, 200);

		RegressionStructureResult result = search.search(nodesById);
		CandidateGraph graph = result.getGraph();

		boolean x1ConnectedToY = graph.hasEdge("x1", "y") || graph.hasEdge("y", "x1");
		boolean x2ConnectedToY = graph.hasEdge("x2", "y") || graph.hasEdge("y", "x2");
		boolean x1ConnectedToX2 = graph.hasEdge("x1", "x2") || graph.hasEdge("x2", "x1");

		Assertions.assertTrue(x1ConnectedToY, "Search should connect the genuinely related pair x1/y");
		Assertions.assertFalse(x2ConnectedToY, "Search should NOT connect the irrelevant variable x2 to y");
		Assertions.assertFalse(x1ConnectedToX2, "Search should NOT connect the two independent variables x1/x2");
		Assertions.assertFalse(result.isIterationCapReached());
	}

	@Test
	public void testForbiddenEdgeConstraintBlocksConnectionEvenWhenBicFavoursIt() throws Exception {
		Random random = new Random(7);
		StringBuilder csv = new StringBuilder("x1,x2,y\n");
		for (int i = 0; i < 300; i++){
			double x1 = random.nextGaussian();
			double x2 = random.nextGaussian();
			double y = 2 + 3 * x1 + random.nextGaussian() * 0.5;
			csv.append(x1).append(",").append(x2).append(",").append(y).append("\n");
		}

		Model model = Model.createModel(new JSONObject(SHELL_MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Map<String, Node> nodesById = new HashMap<>();
		for (Node node : network.getNodeList()){
			nodesById.put(node.getId(), node);
		}

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		RegressionBicScorer scorer = new RegressionBicScorer(dataset);

		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.forbidEdge("x1", "y"); // forbid the connection in EITHER direction

		RegressionStructureSearch search = new RegressionStructureSearch(scorer, knowledge, 5, 200);
		RegressionStructureResult result = search.search(nodesById);
		CandidateGraph graph = result.getGraph();

		Assertions.assertFalse(graph.hasEdge("x1", "y"), "Forbidden connection must never be added in either direction");
		Assertions.assertFalse(graph.hasEdge("y", "x1"), "Forbidden connection must never be added in either direction");
	}

	@Test
	public void testRequiredEdgeIsSeededAndNeverRemoved() throws Exception {
		Random random = new Random(11);
		StringBuilder csv = new StringBuilder("x1,x2,y\n");
		for (int i = 0; i < 200; i++){
			double x1 = random.nextDouble() * 10 - 5;
			double x2 = random.nextDouble() * 10 - 5;
			double y = random.nextDouble() * 10; // y unrelated to x1/x2 in this test - required edge forces it anyway
			csv.append(x1).append(",").append(x2).append(",").append(y).append("\n");
		}

		Model model = Model.createModel(new JSONObject(SHELL_MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Map<String, Node> nodesById = new HashMap<>();
		for (Node node : network.getNodeList()){
			nodesById.put(node.getId(), node);
		}

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		RegressionBicScorer scorer = new RegressionBicScorer(dataset);

		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.requireEdge("x2", "y");

		RegressionStructureSearch search = new RegressionStructureSearch(scorer, knowledge, 5, 200);
		RegressionStructureResult result = search.search(nodesById);
		CandidateGraph graph = result.getGraph();

		Assertions.assertTrue(graph.hasEdge("x2", "y"), "Required edge must be present in the final graph");
	}

	private static final String RANKED_TARGET_MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"x2\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"x3\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"x4\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"type\": \"Ranked\", \"states\": [\"Low\", \"Medium\", \"High\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": []"
			+ "  }]"
			+ "}"
			+ "}";

	/**
	 * Reproduces (in miniature) the real hang: a Ranked target - which can never be {@code simulated} and so always
	 * needs a materialized NPT - individually, genuinely benefits from several simulated-continuous parents (BIC
	 * would want all four here, since each one is independently informative about y). Confirms the search caps how
	 * many it actually adds, rather than accumulating all of them into one combinatorially catastrophic clique - the
	 * exact failure mode that hung real performance evaluation runs before this constraint existed.
	 */
	@Test
	public void testRankedTargetWithManyInformativeContinuousParentsGetsCappedNotAllFour() throws Exception {
		Random random = new Random(3);
		StringBuilder csv = new StringBuilder("y,x1,x2,x3,x4\n");
		String[] states = {"Low", "Medium", "High"};
		for (int i = 0; i < 600; i++){
			double x1 = random.nextGaussian();
			double x2 = random.nextGaussian();
			double x3 = random.nextGaussian();
			double x4 = random.nextGaussian();
			double score = (x1 + x2 + x3 + x4) + random.nextGaussian() * 0.05;
			// Roughly tertile-split the latent score into three ordered states.
			String yState = score < -0.6 ? states[0] : (score > 0.6 ? states[2] : states[1]);
			csv.append(yState).append(",").append(x1).append(",").append(x2).append(",").append(x3).append(",").append(x4).append("\n");
		}

		Model model = Model.createModel(new JSONObject(RANKED_TARGET_MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Map<String, Node> nodesById = new HashMap<>();
		for (Node node : network.getNodeList()){
			nodesById.put(node.getId(), node);
		}

		Map<String, java.util.List<String>> rankedStatesByNodeId = new HashMap<>();
		rankedStatesByNodeId.put("y", java.util.Arrays.asList(states));
		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()), rankedStatesByNodeId);
		RegressionBicScorer scorer = new RegressionBicScorer(dataset);

		// x1 vs y is a roughly-symmetric linear relationship, so BIC alone can't reliably tell the search which
		// direction to prefer (same Markov-equivalence ambiguity as testRecoversRelevantVariableAndIgnoresIrrelevantOne
		// above) - forcing this one edge's direction keeps the test's real point (does the *cap* hold once y already
		// has a materialized-NPT parent load) deterministic, without that unrelated ambiguity affecting the result.
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.requireEdge("x1", "y");

		RegressionStructureSearch search = new RegressionStructureSearch(scorer, knowledge, 5, 200);
		RegressionStructureResult result = search.search(nodesById);
		CandidateGraph graph = result.getGraph();

		long yParentCount = graph.getParents("y").size();
		Assertions.assertTrue(yParentCount >= 1, "The required edge alone guarantees at least one parent");
		Assertions.assertTrue(yParentCount < 4,
				"Search must not accumulate all four simulated-continuous parents into y's clique - got " + yParentCount);
	}
}
