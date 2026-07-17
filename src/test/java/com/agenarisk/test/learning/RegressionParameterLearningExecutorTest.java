package com.agenarisk.test.learning;

import BNlearning.Database;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.RegressionParameterLearningConfigurer;
import com.agenarisk.learning.structure.config.RegressionParameterLearningExecutor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Exercises the full JSON-config -> Configurer -> Executor wiring for the canonical regression parameter learner
 * (the same path {@code RegressionParameterLearningNode} drives from the graph execution system) - covering all
 * three fitting cases in one model: a continuous target (OLS), a categorical target with only categorical parents
 * (baked NPT), and a categorical target with a continuous parent (persisted {@code MultinomialLogit(...)}
 * expression) - plus the enriched per-node diagnostic detail {@link com.agenarisk.learning.structure.regressiondiscovery.RegressionNodeFitter}
 * now reports (partitions/combinations/expression), which {@code RegressionParameterLearningExecutor} passes through
 * unchanged.
 */
public class RegressionParameterLearningExecutorTest {

	{
		Environment.initialize();
	}

	private static final String MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"boolChild\","
			+ "        \"configuration\": {\"type\": \"Boolean\"}"
			+ "      }, {"
			+ "        \"id\": \"catParent\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }, {"
			+ "        \"id\": \"labelChild\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"No\", \"Yes\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"x1\", \"child\": \"y\"},"
			+ "      {\"parent\": \"x1\", \"child\": \"boolChild\"},"
			+ "      {\"parent\": \"catParent\", \"child\": \"labelChild\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testExecutorLearnsAllThreeCasesWithRichDetail() throws Exception {
		Path tempDir = Files.createTempDirectory("regression-parameter-learning-executor-test");
		tempDir.toFile().deleteOnExit();

		Path inputModelPath = tempDir.resolve("input.cmpx");
		Files.write(inputModelPath, new JSONObject(MODEL_JSON).toString().getBytes(StandardCharsets.UTF_8));

		Path dataPath = tempDir.resolve("data.csv");
		StringBuilder csv = new StringBuilder("y,x1,boolChild,catParent,labelChild\n");
		csv.append("2,0,True,False,No\n");
		csv.append("5,1,False,False,No\n");
		csv.append("8,2,True,False,No\n");
		csv.append("11,3,False,True,Yes\n");
		csv.append("14,4,True,True,Yes\n");
		csv.append("17,5,False,True,Yes\n");
		for (int i = 0; i < 10; i++){
			csv.append("20,6,True,False,No\n");
			csv.append("20,6,True,True,Yes\n");
		}
		Files.write(dataPath, csv.toString().getBytes(StandardCharsets.UTF_8));

		Path outputModelPath = tempDir.resolve("output.cmpx");

		Config config = Config.reset((c) -> {
			TempFileCleanup.cleanup(c);
			Database.reset();
		});
		config.setPathInput(dataPath.getParent().toString());
		config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());

		RegressionParameterLearningConfigurer configurer = new RegressionParameterLearningConfigurer(config);

		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("missingValue", "");
		jParams.put("valueSeparator", ",");
		jParams.put("residualMode", RegressionParameterLearningConfigurer.RESIDUAL_MODE_ARITHMETIC);
		jParams.put("minRowsPerPartition", 5);
		jParams.put("dataPath", dataPath.toString());
		jParams.put("modelStageLabel", "stage1");
		jConfig.put("parameters", jParams);
		configurer.configureFromJson(jConfig);

		configurer.setModelStageLabel("stage1");
		configurer.setModelPrefix("regressionParameterLearningTest");
		configurer.setModelPath(outputModelPath);

		Model loadedModel = Model.loadModel(inputModelPath.toString());
		configurer.setModel(loadedModel);

		RegressionParameterLearningExecutor executor = configurer.apply();
		executor.execute();

		JSONObject result = executor.getLastResult();
		Assertions.assertNotNull(result);
		JSONArray jNodes = result.getJSONArray("nodes");

		// Continuous target "y": learned via OLS, rich partition detail present
		JSONObject jY = findNode(jNodes, "y");
		Assertions.assertFalse(jY.getBoolean("skipped"));
		JSONArray jPartitions = jY.getJSONArray("partitions");
		Assertions.assertEquals(1, jPartitions.length());
		Assertions.assertEquals(26, jPartitions.getJSONObject(0).getInt("n"));
		Assertions.assertEquals(1.0, jPartitions.getJSONObject(0).getDouble("r2"), 1e-6);

		// Categorical-only-parents target "labelChild": baked NPT, rich combination detail present
		JSONObject jLabelChild = findNode(jNodes, "labelChild");
		Assertions.assertFalse(jLabelChild.getBoolean("skipped"));
		Assertions.assertEquals(26, jLabelChild.getInt("n"));
		Assertions.assertTrue(jLabelChild.getBoolean("converged"));
		JSONArray jCombinations = jLabelChild.getJSONArray("combinations");
		Assertions.assertEquals(2, jCombinations.length());

		// Categorical-with-continuous-parent target "boolChild": persisted MultinomialLogit expression
		JSONObject jBoolChild = findNode(jNodes, "boolChild");
		Assertions.assertFalse(jBoolChild.getBoolean("skipped"));
		Assertions.assertTrue(jBoolChild.has("expression"));
		Assertions.assertTrue(jBoolChild.getString("expression").startsWith("MultinomialLogit("));

		Assertions.assertTrue(Files.exists(outputModelPath));
		Model writtenModel = Model.loadModel(outputModelPath.toString());
		Network network = writtenModel.getNetworkList().get(0);
		Node writtenX1 = network.getNode("x1");
		Node writtenY = network.getNode("y");
		Node writtenCatParent = network.getNode("catParent");
		Node writtenLabelChild = network.getNode("labelChild");

		DataSet dataSet = writtenModel.createDataSet("ds");
		dataSet.setObservationHard(writtenX1, 10.0);
		writtenModel.calculate();
		CalculationResult cr = dataSet.getCalculationResult(writtenY);
		Assertions.assertEquals(32.0, cr.getMean(), 1e-6);

		dataSet.clearObservations();
		dataSet.setObservationHard(writtenCatParent, "True");
		writtenModel.calculate();
		double probYes = dataSet.getCalculationResult(writtenLabelChild).getResultValue("Yes").getValue();
		Assertions.assertTrue(probYes > 0.5);
	}

	private JSONObject findNode(JSONArray jNodes, String nodeId) {
		for (int i = 0; i < jNodes.length(); i++){
			JSONObject jNode = jNodes.getJSONObject(i);
			if (nodeId.equals(jNode.getString("nodeId"))){
				return jNode;
			}
		}
		throw new AssertionError("No node found with id " + nodeId);
	}
}
