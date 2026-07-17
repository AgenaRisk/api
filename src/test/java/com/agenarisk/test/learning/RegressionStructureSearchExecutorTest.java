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
import java.nio.charset.StandardCharsets;
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
 * {@code RegressionStructureLearningNode} drives from the graph execution system).
 */
public class RegressionStructureSearchExecutorTest {

	{
		Environment.initialize();
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
	public void testExecutorDiscoversStructureAndMaterializesFittedTable() throws Exception {
		Path tempDir = Files.createTempDirectory("regression-structure-search-executor-test");
		tempDir.toFile().deleteOnExit();

		Path inputModelPath = tempDir.resolve("input.cmpx");
		Files.write(inputModelPath, new JSONObject(SHELL_MODEL_JSON).toString().getBytes(StandardCharsets.UTF_8));

		Random random = new Random(123);
		StringBuilder csv = new StringBuilder("x1,x2,y\n");
		for (int i = 0; i < 300; i++){
			double x1 = random.nextGaussian();
			double x2 = random.nextGaussian();
			double y = 2 + 3 * x1 + random.nextGaussian() * 0.5;
			csv.append(x1).append(",").append(x2).append(",").append(y).append("\n");
		}
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, csv.toString().getBytes(StandardCharsets.UTF_8));

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
		jParams.put("modelStageLabel", "stage1");
		jParams.put("maxParentsPerNode", 5);
		jParams.put("maxIterations", 200);
		jConfig.put("parameters", jParams);
		configurer.configureFromJson(jConfig);

		configurer.setModelStageLabel("stage1");
		configurer.setModelPrefix("regressionStructureSearchTest");
		configurer.setModelPath(outputModelPath);

		Model loadedModel = Model.loadModel(inputModelPath.toString());
		configurer.setModel(loadedModel);

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
}
