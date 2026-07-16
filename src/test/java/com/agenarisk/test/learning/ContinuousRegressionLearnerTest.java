package com.agenarisk.test.learning;

import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.OrdinaryLeastSquares;
import com.agenarisk.learning.structure.regression.PartitionEnumerator;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regression.RegressionTableWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.EM.Data;
import uk.co.agena.minerva.util.Environment;

public class ContinuousRegressionLearnerTest {

	{
		Environment.initialize();
	}

	private Data writeAndLoadData(String csv) throws IOException {
		Path tempFile = Files.createTempFile("continuous-regression-test-", ".csv");
		tempFile.toFile().deleteOnExit();
		Files.write(tempFile, csv.getBytes(StandardCharsets.UTF_8));
		try {
			return new Data(tempFile.toString(), "NA", ",");
		}
		catch (Exception ex){
			throw new RuntimeException(ex);
		}
	}

	private static final String MODEL_NO_PARTITION = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": [{\"parent\": \"x1\", \"child\": \"y\"}]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testNoPartitionRecoversLinearRelationship() throws Exception {
		String csv = "y,x1\n"
				+ "2,0\n"
				+ "5,1\n"
				+ "8,2\n"
				+ "11,3\n"
				+ "14,4\n"
				+ "17,5\n";

		Model model = Model.createModel(new JSONObject(MODEL_NO_PARTITION));
		Network network = model.getNetworkList().get(0);
		Node x1 = network.getNode("x1");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.ARITHMETIC);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(1, result.getPartitionResults().size());
		ContinuousRegressionLearner.PartitionResult pr = result.getPartitionResults().get(0);
		Assertions.assertEquals(ContinuousRegressionLearner.FitSource.PARTITION_SPECIFIC, pr.getFitSource());
		Assertions.assertEquals(1.0, pr.getR2(), 1e-6);
		Assertions.assertEquals(6, pr.getN());

		RegressionTableWriter.apply(result);

		DataSet dataSet = model.createDataSet("ds");
		dataSet.setObservationHard(x1, 10.0);
		model.calculate();
		CalculationResult cr = dataSet.getCalculationResult(y);
		Assertions.assertEquals(32.0, cr.getMean(), 1e-6); // y = 2 + 3*x1 = 2 + 30 = 32
	}

	private static final String MODEL_PARTITIONED = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"cat\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }, {"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"cat\", \"child\": \"y\"},"
			+ "      {\"parent\": \"x1\", \"child\": \"y\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testPartitionedByCategoricalParentFitsDistinctSlopesPerState() throws Exception {
		// cat=False: y = 1 + 2*x1 ; cat=True: y = 10 - 1*x1
		String csv = "y,x1,cat\n"
				+ "1,0,False\n"
				+ "3,1,False\n"
				+ "5,2,False\n"
				+ "7,3,False\n"
				+ "9,4,False\n"
				+ "11,5,False\n"
				+ "10,0,True\n"
				+ "9,1,True\n"
				+ "8,2,True\n"
				+ "7,3,True\n"
				+ "6,4,True\n"
				+ "5,5,True\n";

		Model model = Model.createModel(new JSONObject(MODEL_PARTITIONED));
		Network network = model.getNetworkList().get(0);
		Node cat = network.getNode("cat");
		Node x1 = network.getNode("x1");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.ARITHMETIC);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(2, result.getPartitionResults().size());
		for (ContinuousRegressionLearner.PartitionResult pr : result.getPartitionResults()){
			Assertions.assertEquals(ContinuousRegressionLearner.FitSource.PARTITION_SPECIFIC, pr.getFitSource());
			Assertions.assertEquals(1.0, pr.getR2(), 1e-6);
			Assertions.assertEquals(6, pr.getN());
		}

		RegressionTableWriter.apply(result);

		DataSet dataSet = model.createDataSet("ds");

		dataSet.setObservationHard(cat, "False");
		dataSet.setObservationHard(x1, 10.0);
		model.calculate();
		Assertions.assertEquals(21.0, dataSet.getCalculationResult(y).getMean(), 1e-6); // 1 + 2*10

		dataSet.clearObservations();
		dataSet.setObservationHard(cat, "True");
		dataSet.setObservationHard(x1, 10.0);
		model.calculate();
		Assertions.assertEquals(0.0, dataSet.getCalculationResult(y).getMean(), 1e-6); // 10 - 1*10
	}

	private static final String MODEL_PARTITIONED_3STATE = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"cat\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"A\", \"B\", \"C\"]}"
			+ "      }, {"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"cat\", \"child\": \"y\"},"
			+ "      {\"parent\": \"x1\", \"child\": \"y\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testSparsePartitionFallsBackToPooledAncova() throws Exception {
		// A and B have plenty of data (y = 1 + 2*x1 for both, same relationship);
		// C has only 1 row - not enough for its own fit (needs >= 5 by default), so it should fall back to pooled ANCOVA.
		StringBuilder csv = new StringBuilder("y,x1,cat\n");
		for (int i = 0; i < 8; i++){
			csv.append(1 + 2 * i).append(",").append(i).append(",A\n");
			csv.append(1 + 2 * i).append(",").append(i).append(",B\n");
		}
		csv.append("50,3,C\n"); // single row for C, off the A/B trend so we can tell whether it leaked in

		Model model = Model.createModel(new JSONObject(MODEL_PARTITIONED_3STATE));
		Network network = model.getNetworkList().get(0);
		Node cat = network.getNode("cat");
		Node x1 = network.getNode("x1");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.ARITHMETIC);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		Assertions.assertEquals(3, result.getPartitionResults().size());

		ContinuousRegressionLearner.PartitionResult prA = findByState(result, "A");
		ContinuousRegressionLearner.PartitionResult prB = findByState(result, "B");
		ContinuousRegressionLearner.PartitionResult prC = findByState(result, "C");

		Assertions.assertEquals(ContinuousRegressionLearner.FitSource.PARTITION_SPECIFIC, prA.getFitSource());
		Assertions.assertEquals(ContinuousRegressionLearner.FitSource.PARTITION_SPECIFIC, prB.getFitSource());
		Assertions.assertEquals(ContinuousRegressionLearner.FitSource.POOLED_ANCOVA, prC.getFitSource());

		RegressionTableWriter.apply(result);

		// Independently recompute the pooled ANCOVA fit's prediction for cat=C, x1=10 and check it matches what got written
		List<Node> categoricalParents = Arrays.asList(cat);
		List<PartitionEnumerator.Combination> combinations = PartitionEnumerator.enumerate(categoricalParents);
		RegressionDataset.PooledSelection pooled = dataset.selectPooledRows("y", Arrays.asList("x1"), Arrays.asList("cat"), combinations);
		OrdinaryLeastSquares.Result pooledFit = OrdinaryLeastSquares.fit(pooled.getX(), pooled.getY());

		int cIndex = -1;
		for (int i = 0; i < combinations.size(); i++){
			if ("C".equals(combinations.get(i).getState("cat"))){
				cIndex = i;
			}
		}
		double expectedIntercept = pooledFit.getIntercept() + (cIndex > 0 ? pooledFit.getCoefficients()[1 + 1 + (cIndex - 1)] : 0);
		double expectedSlope = pooledFit.getCoefficients()[1];
		double expectedMeanAtX10 = expectedIntercept + expectedSlope * 10;

		DataSet dataSet = model.createDataSet("ds");
		dataSet.setObservationHard(cat, "C");
		dataSet.setObservationHard(x1, 10.0);
		model.calculate();
		Assertions.assertEquals(expectedMeanAtX10, dataSet.getCalculationResult(y).getMean(), 1e-6);
	}

	private ContinuousRegressionLearner.PartitionResult findByState(ContinuousRegressionLearner.NodeLearningResult result, String state) {
		return result.getPartitionResults().stream()
				.filter(pr -> state.equals(pr.getCombination().getState("cat")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No partition result for state " + state));
	}

	@Test
	public void testNormalResidualModeProducesValidExpressionWithPositiveVariance() throws Exception {
		// Noisy but clearly linear: y roughly 2 + 3*x1
		String csv = "y,x1\n"
				+ "2.1,0\n"
				+ "4.9,1\n"
				+ "8.2,2\n"
				+ "10.8,3\n"
				+ "14.1,4\n"
				+ "16.9,5\n";

		Model model = Model.createModel(new JSONObject(MODEL_NO_PARTITION));
		Network network = model.getNetworkList().get(0);
		Node x1 = network.getNode("x1");
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.NORMAL);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		ContinuousRegressionLearner.PartitionResult pr = result.getPartitionResults().get(0);
		Assertions.assertTrue(pr.getExpression().startsWith("Normal("));
		Assertions.assertTrue(pr.getR2() > 0.99);
		Assertions.assertTrue(pr.getResidualVariance() > 0);

		RegressionTableWriter.apply(result);

		DataSet dataSet = model.createDataSet("ds");
		dataSet.setObservationHard(x1, 10.0);
		model.calculate();
		// Mean should still track the fitted line closely even with residual noise/variance in the expression
		double mean = dataSet.getCalculationResult(y).getMean();
		Assertions.assertEquals(32.0, mean, 0.5);
	}

	private static final String MODEL_ROOT_ONLY = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"y\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testConstantParentlessTargetProducesNonZeroVariance() throws Exception {
		// Reproduces a reported production bug: a parentless node whose value never varies across the whole
		// dataset (e.g. a "Fuel_price" column that happened to be constant). The real residual variance there is
		// mathematically 0, but floating-point summation over many rows leaves tiny noise (e.g. 1e-25) rather
		// than an exact 0.0. That noise is still "> 0", so a floor guarded only by positivity let it straight
		// through - and formatNumber's 10-decimal rounding then collapsed it back to a literal "0", producing an
		// illegal Normal(mean, 0) that the calculation engine rejected at calculate() time ("Normal cannot have
		// zero variance"), surfacing as a confusing "node probability table has sum zero probability" error.
		// Confirms the fix: effectiveVariance floors by magnitude (Math.max), not by a raw positivity check.
		StringBuilder csv = new StringBuilder("y\n");
		for (int i = 0; i < 16908; i++){
			csv.append("3\n");
		}

		Model model = Model.createModel(new JSONObject(MODEL_ROOT_ONLY));
		Network network = model.getNetworkList().get(0);
		Node y = network.getNode("y");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData(csv.toString()));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.NORMAL, 5);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(y);

		Assertions.assertFalse(result.isSkipped());
		ContinuousRegressionLearner.PartitionResult pr = result.getPartitionResults().get(0);
		Assertions.assertTrue(pr.getExpression().startsWith("Normal("), "Expected Normal(...), got: " + pr.getExpression());
		Assertions.assertFalse(pr.getExpression().endsWith(", 0)"), "Expression must not have a literal zero variance: " + pr.getExpression());

		RegressionTableWriter.apply(result);

		// The real point of this test: a zero-variance Normal fails at calculation time, not at write time.
		DataSet dataSet = model.createDataSet("ds");
		model.calculate();
		Assertions.assertEquals(3.0, dataSet.getCalculationResult(y).getMean(), 1e-2);
	}

	@Test
	public void testLearnSkipsCategoricalTargetWithContinuousParent() throws Exception {
		String modelJson = "{"
				+ "\"model\": {"
				+ "  \"networks\": [{"
				+ "    \"id\": \"net\","
				+ "    \"nodes\": [{"
				+ "        \"id\": \"x1\","
				+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\"}"
				+ "      }, {"
				+ "        \"id\": \"boolChild\","
				+ "        \"configuration\": {\"type\": \"Boolean\"}"
				+ "      }"
				+ "    ],"
				+ "    \"links\": [{\"parent\": \"x1\", \"child\": \"boolChild\"}]"
				+ "  }]"
				+ "}"
				+ "}";

		Model model = Model.createModel(new JSONObject(modelJson));
		Network network = model.getNetworkList().get(0);
		Node boolChild = network.getNode("boolChild");

		RegressionDataset dataset = new RegressionDataset(writeAndLoadData("boolChild,x1\nTrue,1\nFalse,2\n"));
		ContinuousRegressionLearner learner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.ARITHMETIC);
		ContinuousRegressionLearner.NodeLearningResult result = learner.learn(boolChild);

		Assertions.assertTrue(result.isSkipped());
		Assertions.assertTrue(result.getSkipReason().contains("x1"));
	}
}
