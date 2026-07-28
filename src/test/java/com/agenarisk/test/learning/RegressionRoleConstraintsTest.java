package com.agenarisk.test.learning;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.CategoricalRegressionLearner;
import com.agenarisk.learning.structure.regression.ContinuousRegressionLearner;
import com.agenarisk.learning.structure.regression.LogisticRegressionLearner;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionKnowledge;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionNodeFitter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.EM.Data;
import uk.co.agena.minerva.util.Environment;

/**
 * Exercises the regression-role ({@code forceRegressionRole} / {@code forbidRegressionRole}) and
 * {@code forbidIndicatorEncoding} knowledge constraints as wired through {@link RegressionNodeFitter}.
 */
public class RegressionRoleConstraintsTest {

	{
		Environment.initialize();
	}

	private Data writeAndLoadData(String csv) throws IOException {
		Path tempFile = Files.createTempFile("regression-role-test-", ".csv");
		tempFile.toFile().deleteOnExit();
		Files.write(tempFile, csv.getBytes(StandardCharsets.UTF_8));
		try {
			return new Data(tempFile.toString(), "NA", ",");
		}
		catch (Exception ex){
			throw new RuntimeException(ex);
		}
	}

	private RegressionNodeFitter fitter(Data data, RegressionKnowledge knowledge) {
		RegressionDataset dataset = new RegressionDataset(data);
		ContinuousRegressionLearner continuousLearner = new ContinuousRegressionLearner(dataset, ContinuousRegressionLearner.ResidualMode.NORMAL);
		CategoricalRegressionLearner categoricalLearner = new CategoricalRegressionLearner(dataset);
		LogisticRegressionLearner logisticLearner = new LogisticRegressionLearner(dataset);
		return new RegressionNodeFitter(continuousLearner, categoricalLearner, logisticLearner, knowledge);
	}

	private static final String MODEL_CATEGORICAL_ONLY_TWO_PARENTS = "{"
			+ "\"model\": {\"networks\": [{\"id\": \"net\", \"nodes\": ["
			+ "{\"id\": \"p1\", \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}},"
			+ "{\"id\": \"p2\", \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}},"
			+ "{\"id\": \"y\", \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}}"
			+ "], \"links\": [{\"parent\": \"p1\", \"child\": \"y\"}, {\"parent\": \"p2\", \"child\": \"y\"}]}]}}";

	private static final String MODEL_CONTINUOUS_PARENT = "{"
			+ "\"model\": {\"networks\": [{\"id\": \"net\", \"nodes\": ["
			+ "{\"id\": \"x1\", \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}},"
			+ "{\"id\": \"y\", \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}}"
			+ "], \"links\": [{\"parent\": \"x1\", \"child\": \"y\"}]}]}}";

	/**
	 * XOR-style interaction data over two Boolean parents: y=True is likely when p1 and p2 agree, unlikely when they
	 * disagree. A single additive (main-effects) logit cannot represent this; partitioning on p1 can.
	 */
	private String xorCsv(int nPerCombo) {
		StringBuilder csv = new StringBuilder("y,p1,p2\n");
		appendCombo(csv, "False", "False", 0.9, nPerCombo);
		appendCombo(csv, "False", "True", 0.1, nPerCombo);
		appendCombo(csv, "True", "False", 0.1, nPerCombo);
		appendCombo(csv, "True", "True", 0.9, nPerCombo);
		return csv.toString();
	}

	private void appendCombo(StringBuilder csv, String p1, String p2, double probTrue, int n) {
		int numTrue = (int) Math.round(probTrue * n);
		for (int i = 0; i < n; i++){
			csv.append(i < numTrue ? "True" : "False").append(",").append(p1).append(",").append(p2).append("\n");
		}
	}

	private double probTrueForCombo(JSONObject detail, String p1, String p2) {
		JSONArray combinations = detail.getJSONArray("combinations");
		for (int i = 0; i < combinations.length(); i++){
			JSONObject combo = combinations.getJSONObject(i);
			JSONObject states = combo.getJSONObject("states");
			if (p1.equals(states.getString("p1")) && p2.equals(states.getString("p2"))){
				return combo.getJSONObject("probabilities").getDouble("True");
			}
		}
		throw new AssertionError("No combination for p1=" + p1 + ", p2=" + p2);
	}

	@Test
	public void testDefaultCategoricalOnlyNodeBakesAManualNpt() throws Exception {
		Data data = writeAndLoadData(xorCsv(200));
		Model model = Model.createModel(new JSONObject(MODEL_CATEGORICAL_ONLY_TWO_PARENTS));
		Node y = model.getNetworkList().get(0).getNode("y");

		RegressionNodeFitter.NodeFitOutcome outcome = fitter(data, new RegressionKnowledge()).fitAndWrite(y);

		Assertions.assertFalse(outcome.isSkipped());
		// Default representation for categorical-only parents is a manual NPT: detail carries per-combination rows.
		Assertions.assertTrue(outcome.getDetail().has("combinations"));
		Assertions.assertFalse(outcome.getDetail().has("expression"));
	}

	@Test
	public void testForceRegressionRoleMakesCategoricalOnlyNodeAnExpression() throws Exception {
		Data data = writeAndLoadData(xorCsv(200));
		Model model = Model.createModel(new JSONObject(MODEL_CATEGORICAL_ONLY_TWO_PARENTS));
		Node y = model.getNetworkList().get(0).getNode("y");

		RegressionKnowledge knowledge = new RegressionKnowledge().forceRegressionRole("y");
		RegressionNodeFitter.NodeFitOutcome outcome = fitter(data, knowledge).fitAndWrite(y);

		Assertions.assertFalse(outcome.isSkipped());
		Assertions.assertTrue(outcome.getDetail().has("expression"));
		Assertions.assertTrue(outcome.getDetail().getString("expression").startsWith("MultinomialLogit("));
		Assertions.assertFalse(outcome.getDetail().has("combinations"));
	}

	@Test
	public void testForbidRegressionRoleOnContinuousParentSkipsWithAdvisory() throws Exception {
		// logit(P(True)) = -1 + 2*x1
		StringBuilder csv = new StringBuilder("y,x1\n");
		for (double x : new double[]{-2, -1, 0, 1, 2}){
			double probTrue = 1.0 / (1.0 + Math.exp(-(-1 + 2 * x)));
			int numTrue = (int) Math.round(probTrue * 100);
			for (int i = 0; i < 100; i++){
				csv.append(i < numTrue ? "True" : "False").append(",").append(x).append("\n");
			}
		}
		Data data = writeAndLoadData(csv.toString());
		Model model = Model.createModel(new JSONObject(MODEL_CONTINUOUS_PARENT));
		Node y = model.getNetworkList().get(0).getNode("y");

		RegressionKnowledge knowledge = new RegressionKnowledge().forbidRegressionRole("y");
		RegressionNodeFitter.NodeFitOutcome outcome = fitter(data, knowledge).fitAndWrite(y);

		Assertions.assertTrue(outcome.isSkipped());
		Assertions.assertTrue(outcome.getSkipReason().contains("forbidRegressionRole"));
	}

	@Test
	public void testForbidIndicatorEncodingRecoversInteractionAManualNptCannotPool() throws Exception {
		Data data = writeAndLoadData(xorCsv(300));
		Model model = Model.createModel(new JSONObject(MODEL_CATEGORICAL_ONLY_TWO_PARENTS));
		Network network = model.getNetworkList().get(0);
		Node y = network.getNode("y");

		// Baseline: a single additive main-effects logit cannot represent the XOR interaction - every combination
		// collapses toward the 0.5 marginal.
		JSONObject pooled = fitter(data, new RegressionKnowledge()).fitAndWrite(y).getDetail();
		Assertions.assertEquals(0.5, probTrueForCombo(pooled, "False", "False"), 0.15);
		Assertions.assertEquals(0.5, probTrueForCombo(pooled, "False", "True"), 0.15);

		// Partitioning on p1 lets each p1 slice fit p2 independently, recovering the interaction.
		JSONObject partitioned = fitter(data, new RegressionKnowledge().forbidIndicatorEncoding("p1")).fitAndWrite(y).getDetail();
		Assertions.assertTrue(partitioned.has("partitionedOn"));
		Assertions.assertEquals("p1", partitioned.getJSONArray("partitionedOn").getString(0));
		Assertions.assertEquals(0.9, probTrueForCombo(partitioned, "False", "False"), 0.12);
		Assertions.assertEquals(0.1, probTrueForCombo(partitioned, "False", "True"), 0.12);
		Assertions.assertEquals(0.1, probTrueForCombo(partitioned, "True", "False"), 0.12);
		Assertions.assertEquals(0.9, probTrueForCombo(partitioned, "True", "True"), 0.12);
	}

	@Test
	public void testForceRegressionRoleWithForbidIndicatorProducesPartitionedExpressions() throws Exception {
		Data data = writeAndLoadData(xorCsv(200));
		Model model = Model.createModel(new JSONObject(MODEL_CATEGORICAL_ONLY_TWO_PARENTS));
		Node y = model.getNetworkList().get(0).getNode("y");

		RegressionKnowledge knowledge = new RegressionKnowledge().forceRegressionRole("y").forbidIndicatorEncoding("p1");
		RegressionNodeFitter.NodeFitOutcome outcome = fitter(data, knowledge).fitAndWrite(y);

		Assertions.assertFalse(outcome.isSkipped());
		JSONObject detail = outcome.getDetail();
		Assertions.assertTrue(detail.has("expressions"));
		Assertions.assertEquals("p1", detail.getJSONArray("partitionedOn").getString(0));
		// One MultinomialLogit expression per state of the partitioned parent p1.
		Assertions.assertEquals(2, detail.getJSONArray("expressions").length());
		Assertions.assertTrue(detail.getJSONArray("expressions").getString(0).startsWith("MultinomialLogit("));
	}
}
