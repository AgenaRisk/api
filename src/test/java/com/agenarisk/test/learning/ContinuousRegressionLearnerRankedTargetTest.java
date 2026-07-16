package com.agenarisk.test.learning;

import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regression.RegressionTableWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.EM.Data;
import uk.co.agena.minerva.util.Environment;

/**
 * Reproduces the reported bug: a Ranked target node learned via ContinuousRegressionLearner used to get a
 * {@code Normal(...)} expression written to it, but the core engine's RankedEN only supports {@code TNormal}
 * (Normal and Arithmetic are both rejected at NPT-compile time, even though nothing at the API layer stops you
 * writing them - see Node.setTableFunctions, which never validates against the node's supported function types).
 * Confirms the fix: Ranked targets always get TNormal(mean, variance, lowerBound, upperBound), regardless of the
 * configured ResidualMode.
 */
public class ContinuousRegressionLearnerRankedTargetTest {

	{
		Environment.initialize();
	}

	private Data writeAndLoadData(String csv) throws IOException {
		Path tempFile = Files.createTempFile("continuous-regression-ranked-test-", ".csv");
		tempFile.toFile().deleteOnExit();
		Files.write(tempFile, csv.getBytes(StandardCharsets.UTF_8));
		try {
			return new Data(tempFile.toString(), "NA", ",");
		}
		catch (Exception ex){
			throw new RuntimeException(ex);
		}
	}

	private static final String MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"quality\","
			+ "        \"configuration\": {\"type\": \"Ranked\", \"states\": [\"Low\", \"Medium\", \"High\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": [{\"parent\": \"x1\", \"child\": \"quality\"}]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testRankedTargetGetsTNormalNotNormal() throws Exception {
		// quality's latent [0,1] value rises with x1: at x1=0 it's low, at x1=10 it's high
		String csv = "quality,x1\n"
				+ "0.1,0\n0.15,1\n0.2,2\n0.25,3\n0.9,10\n0.85,9\n0.8,8\n0.75,7\n";

		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Node x1 = network.getNode("x1");
		Node quality = network.getNode("quality");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.NORMAL, 3);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(quality);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(1, result.getPartitionResults().size());
		String expression = result.getPartitionResults().get(0).getExpression();
		Assertions.assertTrue(expression.startsWith("TNormal("), "Expected TNormal(...), got: " + expression);
		Assertions.assertFalse(expression.startsWith("Normal("));

		RegressionTableWriter.apply(result);

		// Written expression must actually be a valid, calculable TNormal for a Ranked node - the real point of
		// this test: it would previously fail here (or produce garbage) with the old Normal(...) expression.
		DataSet dataSet = model.createDataSet("ds");
		dataSet.setObservationHard(x1, 10.0);
		model.calculate();
		CalculationResult cr = dataSet.getCalculationResult(quality);

		double sum = cr.getResultValue("Low").getValue() + cr.getResultValue("Medium").getValue() + cr.getResultValue("High").getValue();
		Assertions.assertEquals(1.0, sum, 1e-6);
		// x1=10 should push probability mass towards "High"
		Assertions.assertTrue(cr.getResultValue("High").getValue() > cr.getResultValue("Low").getValue());
	}

	@Test
	public void testRankedTargetIgnoresArithmeticResidualModeSetting() throws Exception {
		String csv = "quality,x1\n"
				+ "0.1,0\n0.15,1\n0.2,2\n0.25,3\n0.9,10\n0.85,9\n0.8,8\n0.75,7\n";

		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Node quality = network.getNode("quality");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		// Explicitly ask for Arithmetic - RankedEN doesn't support it (or Normal), so this must still emit TNormal.
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.ARITHMETIC, 3);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(quality);

		String expression = result.getPartitionResults().get(0).getExpression();
		Assertions.assertTrue(expression.startsWith("TNormal("), "Expected TNormal(...) even in ARITHMETIC mode, got: " + expression);
	}

	@Test
	public void testRankedBoundsMatchOverallStateRange() throws Exception {
		String csv = "quality,x1\n0.5,0\n0.5,1\n0.5,2\n0.5,3\n";

		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Node quality = network.getNode("quality");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.NORMAL, 3);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(quality);

		String expression = result.getPartitionResults().get(0).getExpression();
		// TNormal(mean, variance, lowerBound, upperBound) - default Ranked states span [0, 1]
		Assertions.assertTrue(expression.endsWith(", 0, 1)"), "Expected bounds 0, 1 at the end of: " + expression);
	}
}
