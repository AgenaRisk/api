package com.agenarisk.test.learning;

import BNlearning.Database;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.RegressionTableLearningConfigurer;
import com.agenarisk.learning.structure.config.RegressionTableLearningExecutor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Exercises the full JSON-config -> Configurer -> Executor wiring (the same path
 * {@code RegressionTableLearningNode} drives from the graph execution system), rather than calling
 * {@code ContinuousRegressionLearner} directly as the other tests in this package do.
 */
public class RegressionTableLearningExecutorTest {

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
	public void testExecutorLearnsContinuousAndReportsSkippedCategorical() throws Exception {
		Path tempDir = Files.createTempDirectory("regression-table-learning-executor-test");
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
		// A few more rows so the categorical fit has more than the bare minimum to work with
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

		RegressionTableLearningConfigurer configurer = new RegressionTableLearningConfigurer(config);

		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("missingValue", "");
		jParams.put("valueSeparator", ",");
		jParams.put("residualMode", RegressionTableLearningConfigurer.RESIDUAL_MODE_ARITHMETIC);
		jParams.put("minRowsPerPartition", 5);
		jParams.put("dataPath", dataPath.toString());
		jParams.put("modelStageLabel", "stage1");
		jConfig.put("parameters", jParams);
		configurer.configureFromJson(jConfig);

		configurer.setModelStageLabel("stage1");
		configurer.setModelPrefix("regressionTableLearningTest");
		configurer.setModelPath(outputModelPath);

		Model loadedModel = Model.loadModel(inputModelPath.toString());
		configurer.setModel(loadedModel);

		RegressionTableLearningExecutor executor = configurer.apply();
		executor.execute();

		// Result JSON reports both the learned node and the skipped one
		JSONObject result = executor.getLastResult();
		Assertions.assertNotNull(result);
		JSONArray jNodes = result.getJSONArray("nodes");

		JSONObject jY = findNode(jNodes, "y");
		Assertions.assertFalse(jY.getBoolean("skipped"));
		JSONArray jPartitions = jY.getJSONArray("partitions");
		Assertions.assertEquals(1, jPartitions.length());
		Assertions.assertEquals(26, jPartitions.getJSONObject(0).getInt("n"));
		Assertions.assertEquals(1.0, jPartitions.getJSONObject(0).getDouble("r2"), 1e-6);

		JSONObject jBoolChild = findNode(jNodes, "boolChild");
		Assertions.assertTrue(jBoolChild.getBoolean("skipped"));
		Assertions.assertTrue(jBoolChild.getString("reason").contains("x1"));

		// The categorical node (catParent -> labelChild) is now actually learned, not skipped as "not implemented"
		JSONObject jLabelChild = findNode(jNodes, "labelChild");
		Assertions.assertFalse(jLabelChild.getBoolean("skipped"));
		Assertions.assertEquals(26, jLabelChild.getInt("n"));
		Assertions.assertTrue(jLabelChild.getBoolean("converged"));
		JSONArray jCombinations = jLabelChild.getJSONArray("combinations");
		Assertions.assertEquals(2, jCombinations.length()); // catParent has 2 states -> 2 combinations

		// Output model was actually written to disk, is loadable, and evaluates the learned relationships correctly
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
		Assertions.assertEquals(32.0, cr.getMean(), 1e-6); // y = 2 + 3*x1

		dataSet.clearObservations();
		dataSet.setObservationHard(writtenCatParent, "True");
		writtenModel.calculate();
		double probYes = dataSet.getCalculationResult(writtenLabelChild).getResultValue("Yes").getValue();
		Assertions.assertTrue(probYes > 0.5); // catParent=True was consistently paired with labelChild=Yes in the data
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
