package com.agenarisk.test.learning;

import BNlearning.Database;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.RegressionStructureConfigurer;
import com.agenarisk.learning.structure.config.RegressionStructureSearchExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Exercises the full JSON-config -> Configurer -> Executor wiring for Regression Structure Discovery (the same path
 * {@code RegressionStructureLearningNode} drives from the graph execution system) - entirely from a bare CSV, no
 * pre-existing {@code .cmpx} model anywhere: node types are declared via the {@code variables} option (or defaulted
 * by {@code ShellModelBuilder} when omitted), exactly like {@code modelDiscovery}/{@code modelGeneration}.
 */
public class RegressionStructureSearchExecutorTest {

	{
		Environment.initialize();
	}

	@Test
	public void testExecutorDiscoversStructureAndMaterializesFittedTableFromBareCsv() throws Exception {
		Path tempDir = Files.createTempDirectory("regression-structure-search-executor-test");
		tempDir.toFile().deleteOnExit();

		Random random = new Random(123);
		StringBuilder csv = new StringBuilder("x1,x2,y\n");
		for (int i = 0; i < 300; i++){
			double x1 = random.nextGaussian();
			double x2 = random.nextGaussian();
			double y = 2 + 3 * x1 + random.nextGaussian() * 0.5;
			csv.append(x1).append(",").append(x2).append(",").append(y).append("\n");
		}
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

		Path outputModelPath = tempDir.resolve("output.cmpx");

		Config config = Config.reset((c) -> {
			TempFileCleanup.cleanup(c);
			Database.reset();
		});
		config.setPathInput(dataPath.getParent().toString());
		config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());

		RegressionStructureConfigurer configurer = new RegressionStructureConfigurer(config);

		// No explicit "variables" spec at all here - x1/x2/y are all numeric, so ShellModelBuilder should default
		// every one of them to a simulated ContinuousInterval with no further input needed.
		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("dataPath", dataPath.toString());
		jParams.put("maxParentsPerNode", 5);
		jParams.put("maxIterations", 200);
		jConfig.put("parameters", jParams);
		configurer.configureFromJson(jConfig);
		configurer.setModelPath(outputModelPath);

		RegressionStructureSearchExecutor executor = configurer.apply();
		executor.execute();

		JSONObject result = executor.getLastResult();
		Assertions.assertNotNull(result);
		Assertions.assertTrue(result.has("bicScore"));
		Assertions.assertTrue(result.has("logLikelihoodScore"));
		Assertions.assertTrue(result.has("freeParameters"));
		Assertions.assertTrue(result.has("edges"));

		JSONArray jEdges = result.getJSONArray("edges");
		boolean x1ConnectedToY = false;
		for (int i = 0; i < jEdges.length(); i++){
			JSONObject jEdge = jEdges.getJSONObject(i);
			if (("x1".equals(jEdge.getString("parent")) && "y".equals(jEdge.getString("child")))
					|| ("y".equals(jEdge.getString("parent")) && "x1".equals(jEdge.getString("child")))){
				x1ConnectedToY = true;
			}
			// x2 must not appear connected to y at all
			boolean isX2Y = ("x2".equals(jEdge.getString("parent")) && "y".equals(jEdge.getString("child")))
					|| ("y".equals(jEdge.getString("parent")) && "x2".equals(jEdge.getString("child")));
			Assertions.assertFalse(isX2Y, "x2 should not be connected to y");
		}
		Assertions.assertTrue(x1ConnectedToY, "x1 and y should end up connected");

		Assertions.assertTrue(Files.exists(outputModelPath));
		Model writtenModel = Model.loadModel(outputModelPath.toString());
		Network network = writtenModel.getNetworkList().get(0);

		// Whichever direction was discovered, the model should be internally consistent and calculable
		DataSet dataSet = writtenModel.createDataSet("ds");
		writtenModel.calculate();
		Node writtenY = network.getNode("y");
		CalculationResult cr = dataSet.getCalculationResult(writtenY);
		Assertions.assertFalse(Double.isNaN(cr.getMean()));
	}

	@Test
	public void testExplicitVariableDeclarationsRespectRankedOrderingAndMixedTypes() throws Exception {
		Path tempDir = Files.createTempDirectory("regression-structure-search-executor-variables-test");
		tempDir.toFile().deleteOnExit();

		Random random = new Random(7);
		String[] rankStates = {"Low", "Medium", "High"};
		StringBuilder csv = new StringBuilder("x1,cat,rank,y\n");
		for (int i = 0; i < 300; i++){
			double x1 = random.nextGaussian();
			String cat = random.nextBoolean() ? "True" : "False";
			String rank = rankStates[random.nextInt(rankStates.length)];
			double y = 2 + 3 * x1 + random.nextGaussian() * 0.5;
			csv.append(x1).append(",").append(cat).append(",").append(rank).append(",").append(y).append("\n");
		}
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

		Path outputModelPath = tempDir.resolve("output.cmpx");

		Config config = Config.reset((c) -> {
			TempFileCleanup.cleanup(c);
			Database.reset();
		});
		config.setPathInput(dataPath.getParent().toString());
		config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());

		RegressionStructureConfigurer configurer = new RegressionStructureConfigurer(config);

		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("dataPath", dataPath.toString());
		jParams.put("maxParentsPerNode", 5);
		jParams.put("maxIterations", 100);
		jConfig.put("parameters", jParams);

		JSONObject jVariables = new JSONObject();
		jVariables.put("cat", new JSONObject().put("type", "Boolean"));
		jVariables.put("rank", new JSONObject().put("type", "Ranked").put("states", new JSONArray(rankStates)));
		jConfig.put("variables", jVariables);
		configurer.configureFromJson(jConfig);
		configurer.setModelPath(outputModelPath);

		RegressionStructureSearchExecutor executor = configurer.apply();
		executor.execute();

		Assertions.assertNotNull(executor.getLastResult());
		Assertions.assertTrue(Files.exists(outputModelPath));

		Model writtenModel = Model.loadModel(outputModelPath.toString());
		Network network = writtenModel.getNetworkList().get(0);

		Node catNode = network.getNode("cat");
		Assertions.assertEquals(Node.Type.Boolean, catNode.getType());

		Node rankNode = network.getNode("rank");
		Assertions.assertEquals(Node.Type.Ranked, rankNode.getType());
		Assertions.assertEquals(3, rankNode.getStates().size());
		Assertions.assertEquals("Low", rankNode.getStates().get(0).getLabel());
		Assertions.assertEquals("Medium", rankNode.getStates().get(1).getLabel());
		Assertions.assertEquals("High", rankNode.getStates().get(2).getLabel());

		Node x1Node = network.getNode("x1");
		Assertions.assertEquals(Node.Type.ContinuousInterval, x1Node.getType());
	}
}
