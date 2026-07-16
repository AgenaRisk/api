package com.agenarisk.test.learning;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.RegressionEligibility;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

public class RegressionEligibilityTest {

	{
		Environment.initialize();
	}

	private static final String MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"contParent\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\"}"
			+ "      }, {"
			+ "        \"id\": \"catParent\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"A\", \"B\"]}"
			+ "      }, {"
			+ "        \"id\": \"boolChildOfContinuous\","
			+ "        \"configuration\": {\"type\": \"Boolean\"}"
			+ "      }, {"
			+ "        \"id\": \"boolChildOfCategorical\","
			+ "        \"configuration\": {\"type\": \"Boolean\"}"
			+ "      }, {"
			+ "        \"id\": \"rankedChildOfContinuous\","
			+ "        \"configuration\": {\"type\": \"Ranked\", \"states\": [\"Low\", \"High\"]}"
			+ "      }, {"
			+ "        \"id\": \"contChildOfContinuous\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\"}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"contParent\", \"child\": \"boolChildOfContinuous\"},"
			+ "      {\"parent\": \"catParent\", \"child\": \"boolChildOfCategorical\"},"
			+ "      {\"parent\": \"contParent\", \"child\": \"rankedChildOfContinuous\"},"
			+ "      {\"parent\": \"contParent\", \"child\": \"contChildOfContinuous\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	private Network loadNetwork() throws Exception {
		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		return model.getNetworkList().get(0);
	}

	@Test
	public void testCategoricalTargetWithContinuousParentIsIneligible() throws Exception {
		Network network = loadNetwork();
		Node target = network.getNode("boolChildOfContinuous");

		RegressionEligibility.Decision decision = RegressionEligibility.evaluate(target);

		Assertions.assertFalse(decision.isEligible());
		Assertions.assertTrue(decision.getReason().contains("contParent"));
	}

	@Test
	public void testCategoricalTargetWithOnlyCategoricalParentsIsEligible() throws Exception {
		Network network = loadNetwork();
		Node target = network.getNode("boolChildOfCategorical");

		RegressionEligibility.Decision decision = RegressionEligibility.evaluate(target);

		Assertions.assertTrue(decision.isEligible());
		Assertions.assertNull(decision.getReason());
	}

	@Test
	public void testRankedTargetWithContinuousParentIsEligible() throws Exception {
		Network network = loadNetwork();
		Node target = network.getNode("rankedChildOfContinuous");

		RegressionEligibility.Decision decision = RegressionEligibility.evaluate(target);

		Assertions.assertTrue(decision.isEligible());
	}

	@Test
	public void testContinuousTargetWithContinuousParentIsEligible() throws Exception {
		Network network = loadNetwork();
		Node target = network.getNode("contChildOfContinuous");

		RegressionEligibility.Decision decision = RegressionEligibility.evaluate(target);

		Assertions.assertTrue(decision.isEligible());
	}
}
