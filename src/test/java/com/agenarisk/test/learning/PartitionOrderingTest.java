package com.agenarisk.test.learning;

import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Empirically determines the row-major enumeration order the core engine uses
 * for partitioned expressions with more than one partition parent, since this
 * is not obviously documented and the OLS table learner needs to emit
 * expressions in the exact order the engine expects.
 */
public class PartitionOrderingTest {

	{
		Environment.initialize();
	}

	private static final String MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"b1\","
			+ "        \"configuration\": {"
			+ "          \"type\": \"Boolean\","
			+ "          \"states\": [\"False\", \"True\"]"
			+ "        }"
			+ "      }, {"
			+ "        \"id\": \"b2\","
			+ "        \"configuration\": {"
			+ "          \"type\": \"Boolean\","
			+ "          \"states\": [\"False\", \"True\"]"
			+ "        }"
			+ "      }, {"
			+ "        \"id\": \"c\","
			+ "        \"configuration\": {"
			+ "          \"simulated\": true,"
			+ "          \"type\": \"ContinuousInterval\","
			+ "          \"table\": {"
			+ "            \"type\": \"Partitioned\","
			+ "            \"partitions\": [\"b1\", \"b2\"],"
			+ "            \"expressions\": ["
			+ "              \"Arithmetic(10)\","
			+ "              \"Arithmetic(20)\","
			+ "              \"Arithmetic(30)\","
			+ "              \"Arithmetic(40)\""
			+ "            ]"
			+ "          }"
			+ "        }"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"b1\", \"child\": \"c\"},"
			+ "      {\"parent\": \"b2\", \"child\": \"c\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testTwoParentPartitionOrder() throws Exception {
		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Node b1 = network.getNode("b1");
		Node b2 = network.getNode("b2");
		Node c = network.getNode("c");

		double meanFalseFalse = observeAndGetMean(model, network, b1, "False", b2, "False", c);
		double meanFalseTrue = observeAndGetMean(model, network, b1, "False", b2, "True", c);
		double meanTrueFalse = observeAndGetMean(model, network, b1, "True", b2, "False", c);
		double meanTrueTrue = observeAndGetMean(model, network, b1, "True", b2, "True", c);

		System.out.println("b1=False,b2=False -> " + meanFalseFalse);
		System.out.println("b1=False,b2=True  -> " + meanFalseTrue);
		System.out.println("b1=True,b2=False  -> " + meanTrueFalse);
		System.out.println("b1=True,b2=True   -> " + meanTrueTrue);

		// Just make sure all four came out distinct so we actually learn something
		Assertions.assertNotEquals(meanFalseFalse, meanFalseTrue);
		Assertions.assertNotEquals(meanFalseFalse, meanTrueFalse);
		Assertions.assertNotEquals(meanFalseFalse, meanTrueTrue);
	}

	private double observeAndGetMean(Model model, Network network, Node b1, String s1, Node b2, String s2, Node c) throws Exception {
		DataSet dataSet = model.getDataSetList().isEmpty() ? model.createDataSet("ds") : model.getDataSetList().get(0);
		dataSet.clearObservations();
		dataSet.setObservationHard(b1, s1);
		dataSet.setObservationHard(b2, s2);
		model.calculate();
		CalculationResult cr = dataSet.getCalculationResult(c);
		return cr.getMean();
	}

	private static final String MODEL_JSON_2X3 = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"b1\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }, {"
			+ "        \"id\": \"cat\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"Low\", \"Mid\", \"High\"]}"
			+ "      }, {"
			+ "        \"id\": \"c\","
			+ "        \"configuration\": {"
			+ "          \"simulated\": true,"
			+ "          \"type\": \"ContinuousInterval\","
			+ "          \"table\": {"
			+ "            \"type\": \"Partitioned\","
			+ "            \"partitions\": [\"b1\", \"cat\"],"
			+ "            \"expressions\": ["
			+ "              \"Arithmetic(1)\", \"Arithmetic(2)\", \"Arithmetic(3)\","
			+ "              \"Arithmetic(4)\", \"Arithmetic(5)\", \"Arithmetic(6)\""
			+ "            ]"
			+ "          }"
			+ "        }"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"b1\", \"child\": \"c\"},"
			+ "      {\"parent\": \"cat\", \"child\": \"c\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testTwoByThreePartitionOrderMatchesEnumerator() throws Exception {
		Model model = Model.createModel(new JSONObject(MODEL_JSON_2X3));
		Network network = model.getNetworkList().get(0);
		Node b1 = network.getNode("b1");
		Node cat = network.getNode("cat");
		Node c = network.getNode("c");

		String[] b1States = {"False", "True"};
		String[] catStates = {"Low", "Mid", "High"};
		double expected = 1;
		for (String b1State : b1States){
			for (String catState : catStates){
				double mean = observeAndGetMean(model, network, b1, b1State, cat, catState, c);
				Assertions.assertEquals(expected, mean, 1e-9,
						"b1=" + b1State + ",cat=" + catState + " should map to expression #" + (int) expected);
				expected++;
			}
		}
	}
}
