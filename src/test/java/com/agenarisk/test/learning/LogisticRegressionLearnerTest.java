package com.agenarisk.test.learning;

import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.LogisticExpressionTableWriter;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.EM.Data;
import uk.co.agena.minerva.util.Environment;

public class LogisticRegressionLearnerTest {

	{
		Environment.initialize();
	}

	private Data writeAndLoadData(String csv) throws IOException {
		Path tempFile = Files.createTempFile("logistic-regression-test-", ".csv");
		tempFile.toFile().deleteOnExit();
		Files.write(tempFile, csv.getBytes(StandardCharsets.UTF_8));
		try {
			return new Data(tempFile.toString(), "NA", ",");
		}
		catch (Exception ex){
			throw new RuntimeException(ex);
		}
	}

	private static final String MODEL_CONTINUOUS_PARENT_ONLY = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": [{\"parent\": \"x1\", \"child\": \"y\"}]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testContinuousOnlyParentRecoversKnownLogitModel() throws Exception {
		// Data generated from an EXACT logit model: logit(P(True)) = -1 + 2*x1, x1 in {-2,-1,0,1,2}
		double[] xs = {-2, -1, 0, 1, 2};
		int n = 200;
		StringBuilder csv = new StringBuilder("y,x1\n");
		for (double x : xs){
			double probTrue = sigmoid(-1 + 2 * x);
			appendRows(csv, x, probTrue, n);
		}

		Model model = Model.createModel(new JSONObject(MODEL_CONTINUOUS_PARENT_ONLY));
		Network network = model.getNetworkList().get(0);
		Node x1 = network.getNode("x1");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		LogisticRegressionLearner learner = new LogisticRegressionLearner(dataset);
		LogisticRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(xs.length * n, result.getN());
		Assertions.assertTrue(result.getExpression().startsWith("MultinomialLogit("));
		Assertions.assertTrue(result.getExpression().contains(x1.getId()));

		LogisticExpressionTableWriter.apply(result);

		for (double x : xs){
			DataSet dataSet = model.getDataSetList().isEmpty() ? model.createDataSet("ds") : model.getDataSetList().get(0);
			dataSet.clearObservations();
			dataSet.setObservationHard(x1, x);
			model.calculate();
			CalculationResult cr = dataSet.getCalculationResult(y);
			double actual = cr.getResultValue("True").getValue();
			double expected = sigmoid(-1 + 2 * x);
			Assertions.assertEquals(expected, actual, 0.1);
		}
	}

	private static final String MODEL_MIXED_PARENTS = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"cat\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"x1\", \"child\": \"y\"},"
			+ "      {\"parent\": \"cat\", \"child\": \"y\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testMixedContinuousAndCategoricalParentsRecoversKnownLogitModel() throws Exception {
		// logit(P(True)) = -1 + 1.5*x1 + 1*[cat=True]
		double[] xs = {-1, 0, 1};
		String[] cats = {"False", "True"};
		int n = 200;
		StringBuilder csv = new StringBuilder("y,x1,cat\n");
		for (double x : xs){
			for (String cat : cats){
				double logit = -1 + 1.5 * x + ("True".equals(cat) ? 1 : 0);
				appendRows(csv, x, cat, sigmoid(logit), n);
			}
		}

		Model model = Model.createModel(new JSONObject(MODEL_MIXED_PARENTS));
		Network network = model.getNetworkList().get(0);
		Node x1 = network.getNode("x1");
		Node cat = network.getNode("cat");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		LogisticRegressionLearner learner = new LogisticRegressionLearner(dataset);
		LogisticRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertTrue(result.getExpression().contains("Indicator(" + cat.getId()));

		LogisticExpressionTableWriter.apply(result);

		for (double x : xs){
			for (String c : cats){
				DataSet dataSet = model.getDataSetList().isEmpty() ? model.createDataSet("ds") : model.getDataSetList().get(0);
				dataSet.clearObservations();
				dataSet.setObservationHard(x1, x);
				dataSet.setObservationHard(cat, c);
				model.calculate();
				double actual = dataSet.getCalculationResult(y).getResultValue("True").getValue();
				double expected = sigmoid(-1 + 1.5 * x + ("True".equals(c) ? 1 : 0));
				Assertions.assertEquals(expected, actual, 0.12);
			}
		}
	}

	@Test
	public void testCategoricalOnlyParentsAreDeclined() throws Exception {
		String csv = "y,p1\nTrue,True\nFalse,False\n";
		Model model = Model.createModel(new JSONObject(
				"{\"model\": {\"networks\": [{\"id\": \"net\", \"nodes\": ["
				+ "{\"id\": \"p1\", \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}},"
				+ "{\"id\": \"y\", \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}}"
				+ "], \"links\": [{\"parent\": \"p1\", \"child\": \"y\"}]}]}}"));
		Network network = model.getNetworkList().get(0);
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		LogisticRegressionLearner learner = new LogisticRegressionLearner(dataset);
		LogisticRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertTrue(result.isSkipped());
		Assertions.assertTrue(result.getSkipReason().contains("CategoricalRegressionLearner"));
	}

	private void appendRows(StringBuilder csv, double x1, double probTrue, int n) {
		int numTrue = (int) Math.round(probTrue * n);
		for (int i = 0; i < n; i++){
			csv.append(i < numTrue ? "True" : "False").append(",").append(x1).append("\n");
		}
	}

	private void appendRows(StringBuilder csv, double x1, String cat, double probTrue, int n) {
		int numTrue = (int) Math.round(probTrue * n);
		for (int i = 0; i < n; i++){
			csv.append(i < numTrue ? "True" : "False").append(",").append(x1).append(",").append(cat).append("\n");
		}
	}

	private double sigmoid(double logit) {
		return 1.0 / (1.0 + Math.exp(-logit));
	}
}
