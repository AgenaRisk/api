package com.agenarisk.test.learning;

import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.CategoricalTableWriter;
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

public class CategoricalRegressionLearnerTest {

	{
		Environment.initialize();
	}

	private Data writeAndLoadData(String csv) throws IOException {
		Path tempFile = Files.createTempFile("categorical-regression-test-", ".csv");
		tempFile.toFile().deleteOnExit();
		Files.write(tempFile, csv.getBytes(StandardCharsets.UTF_8));
		try {
			return new Data(tempFile.toString(), "NA", ",");
		}
		catch (Exception ex){
			throw new RuntimeException(ex);
		}
	}

	private static final String MODEL_ROOT = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"cat\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"A\", \"B\", \"C\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": []"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testRootNodeRecoversEmpiricalProportionsExactly() throws Exception {
		String csv = "cat\nA\nA\nA\nA\nA\nA\nB\nB\nC\nC\n"; // A:6/10, B:2/10, C:2/10

		Model model = Model.createModel(new JSONObject(MODEL_ROOT));
		Network network = model.getNetworkList().get(0);
		Node cat = network.getNode("cat");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		CategoricalRegressionLearner learner = new CategoricalRegressionLearner(dataset);
		CategoricalRegressionLearner.NodeLearningResult result = learner.learn(cat);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(10, result.getN());
		Assertions.assertTrue(result.isConverged());

		CategoricalTableWriter.apply(result);

		DataSet dataSet = model.createDataSet("ds");
		model.calculate();
		CalculationResult cr = dataSet.getCalculationResult(cat);
		Assertions.assertEquals(0.6, cr.getResultValue("A").getValue(), 1e-6);
		Assertions.assertEquals(0.2, cr.getResultValue("B").getValue(), 1e-6);
		Assertions.assertEquals(0.2, cr.getResultValue("C").getValue(), 1e-6);
	}

	private static final String MODEL_TWO_PARENTS = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"p1\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }, {"
			+ "        \"id\": \"p2\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"No\", \"Yes\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"p1\", \"child\": \"y\"},"
			+ "      {\"parent\": \"p2\", \"child\": \"y\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testTwoParentsRecoversAdditiveLogOddsStructure() throws Exception {
		// Data generated from an EXACT additive logit model: logit(P(Yes)) = -1 + 1.5*[p1=True] + 0.5*[p2=True]
		// (no interaction term) - this is exactly what a main-effects multinomial logit can represent, so recovered
		// probabilities should closely match the true generating probabilities even though individual combo counts
		// are finite samples, not exact proportions.
		double probFF = sigmoid(-1);
		double probFT = sigmoid(-1 + 0.5);
		double probTF = sigmoid(-1 + 1.5);
		double probTT = sigmoid(-1 + 1.5 + 0.5);

		int n = 300;
		StringBuilder csv = new StringBuilder("y,p1,p2\n");
		appendRows(csv, "False", "False", probFF, n);
		appendRows(csv, "False", "True", probFT, n);
		appendRows(csv, "True", "False", probTF, n);
		appendRows(csv, "True", "True", probTT, n);

		Model model = Model.createModel(new JSONObject(MODEL_TWO_PARENTS));
		Network network = model.getNetworkList().get(0);
		Node p1 = network.getNode("p1");
		Node p2 = network.getNode("p2");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		CategoricalRegressionLearner learner = new CategoricalRegressionLearner(dataset);
		CategoricalRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(4 * n, result.getN());
		Assertions.assertTrue(result.getPseudoR2() > 0.05);

		CategoricalTableWriter.apply(result);

		assertLearnedProbabilityCloseTo(model, p1, "False", p2, "False", y, "Yes", probFF, 0.08);
		assertLearnedProbabilityCloseTo(model, p1, "False", p2, "True", y, "Yes", probFT, 0.08);
		assertLearnedProbabilityCloseTo(model, p1, "True", p2, "False", y, "Yes", probTF, 0.08);
		assertLearnedProbabilityCloseTo(model, p1, "True", p2, "True", y, "Yes", probTT, 0.08);
	}

	@Test
	public void testSparseCombinationStillProducesValidDistribution() throws Exception {
		// (True,True) combination has only a single row - main effects + ridge should still produce a sane,
		// well-formed probability distribution for it (borrowing strength from the other combinations) rather than
		// failing or producing something degenerate.
		StringBuilder csv = new StringBuilder("y,p1,p2\n");
		appendRows(csv, "False", "False", 0.2, 50);
		appendRows(csv, "False", "True", 0.4, 50);
		appendRows(csv, "True", "False", 0.6, 50);
		csv.append("Yes,True,True\n");

		Model model = Model.createModel(new JSONObject(MODEL_TWO_PARENTS));
		Network network = model.getNetworkList().get(0);
		Node p1 = network.getNode("p1");
		Node p2 = network.getNode("p2");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		CategoricalRegressionLearner learner = new CategoricalRegressionLearner(dataset);
		CategoricalRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertTrue(result.isConverged());
		for (double[] row : result.getNpt()){
			double sum = row[0] + row[1];
			Assertions.assertEquals(1.0, sum, 1e-6);
			for (double p : row){
				Assertions.assertTrue(p >= 0 && p <= 1);
			}
		}

		CategoricalTableWriter.apply(result);

		DataSet dataSet = model.createDataSet("ds");
		dataSet.setObservationHard(p1, "True");
		dataSet.setObservationHard(p2, "True");
		model.calculate();
		double probYes = dataSet.getCalculationResult(y).getResultValue("Yes").getValue();
		Assertions.assertTrue(probYes > 0 && probYes < 1);
	}

	@Test
	public void testMissingValuesExcludedIndependently() throws Exception {
		String csv = "y,p1,p2\n"
				+ "Yes,True,True\n"
				+ "NA,True,True\n" // target missing - excluded
				+ "Yes,NA,True\n" // p1 missing - excluded
				+ "No,False,False\n"
				+ "No,False,False\n";

		Model model = Model.createModel(new JSONObject(MODEL_TWO_PARENTS));
		Network network = model.getNetworkList().get(0);
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		CategoricalRegressionLearner learner = new CategoricalRegressionLearner(dataset);
		CategoricalRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(3, result.getN()); // only the 3 fully-complete rows
	}

	private void appendRows(StringBuilder csv, String p1, String p2, double probYes, int n) {
		int numYes = (int) Math.round(probYes * n);
		for (int i = 0; i < n; i++){
			csv.append(i < numYes ? "Yes" : "No").append(",").append(p1).append(",").append(p2).append("\n");
		}
	}

	private double sigmoid(double logit) {
		return 1.0 / (1.0 + Math.exp(-logit));
	}

	private void assertLearnedProbabilityCloseTo(Model model, Node p1, String s1, Node p2, String s2, Node y, String state, double expected, double tolerance) throws Exception {
		DataSet dataSet = model.getDataSetList().isEmpty() ? model.createDataSet("ds") : model.getDataSetList().get(0);
		dataSet.clearObservations();
		dataSet.setObservationHard(p1, s1);
		dataSet.setObservationHard(p2, s2);
		model.calculate();
		double actual = dataSet.getCalculationResult(y).getResultValue(state).getValue();
		Assertions.assertEquals(expected, actual, tolerance);
	}
}
