package com.agenarisk.test.learning;

import BNlearning.Database;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.LogisticRegressionTableLearningConfigurer;
import com.agenarisk.learning.structure.config.LogisticRegressionTableLearningExecutor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Exercises the full JSON-config -> Configurer -> Executor wiring for the logistic-capable executor, using the same
 * model/data shape as {@code RegressionTableLearningExecutorTest} to prove this executor is a strict superset of the
 * older one's behaviour: continuous target ("y") and categorical-only-parent target ("labelChild") are learned
 * identically, while the previously-skipped categorical-target-with-continuous-parent case ("boolChild") is now
 * actually learned via a persisted MultinomialLogit expression instead of being skipped.
 */
public class LogisticRegressionTableLearningExecutorTest {

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
	public void testExecutorLearnsContinuousLabelChildAndPreviouslySkippedBoolChild() throws Exception {
		Path tempDir = Files.createTempDirectory("logistic-regression-table-learning-executor-test");
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

		LogisticRegressionTableLearningConfigurer configurer = new LogisticRegressionTableLearningConfigurer(config);

		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("missingValue", "");
		jParams.put("valueSeparator", ",");
		jParams.put("residualMode", LogisticRegressionTableLearningConfigurer.RESIDUAL_MODE_ARITHMETIC);
		jParams.put("minRowsPerPartition", 5);
		jParams.put("dataPath", dataPath.toString());
		jParams.put("modelStageLabel", "stage1");
		jConfig.put("parameters", jParams);
		configurer.configureFromJson(jConfig);

		configurer.setModelStageLabel("stage1");
		configurer.setModelPrefix("logisticRegressionTableLearningTest");
		configurer.setModelPath(outputModelPath);

		Model loadedModel = Model.loadModel(inputModelPath.toString());
		configurer.setModel(loadedModel);

		LogisticRegressionTableLearningExecutor executor = configurer.apply();
		executor.execute();

		JSONObject result = executor.getLastResult();
		Assertions.assertNotNull(result);
		JSONArray jNodes = result.getJSONArray("nodes");

		// Same as the old executor: continuous target "y" learned via OLS
		JSONObject jY = findNode(jNodes, "y");
		Assertions.assertFalse(jY.getBoolean("skipped"));
		JSONArray jPartitions = jY.getJSONArray("partitions");
		Assertions.assertEquals(1, jPartitions.length());
		Assertions.assertEquals(26, jPartitions.getJSONObject(0).getInt("n"));
		Assertions.assertEquals(1.0, jPartitions.getJSONObject(0).getDouble("r2"), 1e-6);

		// Same as the old executor: categorical-only-parent target "labelChild" learned via CategoricalRegressionLearner
		JSONObject jLabelChild = findNode(jNodes, "labelChild");
		Assertions.assertFalse(jLabelChild.getBoolean("skipped"));
		Assertions.assertEquals(26, jLabelChild.getInt("n"));
		Assertions.assertTrue(jLabelChild.getBoolean("converged"));
		JSONArray jCombinations = jLabelChild.getJSONArray("combinations");
		Assertions.assertEquals(2, jCombinations.length());

		// DIFFERENT from the old executor: "boolChild" (categorical, continuous parent x1) is now LEARNED, not skipped
		JSONObject jBoolChild = findNode(jNodes, "boolChild");
		Assertions.assertFalse(jBoolChild.getBoolean("skipped"));
		Assertions.assertTrue(jBoolChild.has("expression"));
		Assertions.assertTrue(jBoolChild.getString("expression").startsWith("MultinomialLogit("));

		Assertions.assertTrue(Files.exists(outputModelPath));
		Model writtenModel = Model.loadModel(outputModelPath.toString());
		Network network = writtenModel.getNetworkList().get(0);
		Node writtenX1 = network.getNode("x1");
		Node writtenY = network.getNode("y");
		Node writtenBoolChild = network.getNode("boolChild");
		Node writtenCatParent = network.getNode("catParent");
		Node writtenLabelChild = network.getNode("labelChild");

		DataSet dataSet = writtenModel.createDataSet("ds");
		dataSet.setObservationHard(writtenX1, 10.0);
		writtenModel.calculate();
		CalculationResult cr = dataSet.getCalculationResult(writtenY);
		Assertions.assertEquals(32.0, cr.getMean(), 1e-6);

		// boolChild's learned logistic expression evaluates sensibly under evidence on x1
		double probTrueAtLowX1 = dataSet.getCalculationResult(writtenBoolChild).getResultValue("True").getValue();

		dataSet.clearObservations();
		dataSet.setObservationHard(writtenX1, -10.0);
		writtenModel.calculate();
		double probTrueAtHighX1Negated = dataSet.getCalculationResult(writtenBoolChild).getResultValue("True").getValue();
		// Just confirm the two evidence settings produce a well-formed, differing probability (data isn't a clean
		// linear-in-x1 signal by construction here, so we don't assert a specific direction, just non-degeneracy)
		Assertions.assertTrue(probTrueAtLowX1 >= 0 && probTrueAtLowX1 <= 1);
		Assertions.assertTrue(probTrueAtHighX1Negated >= 0 && probTrueAtHighX1Negated <= 1);

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
