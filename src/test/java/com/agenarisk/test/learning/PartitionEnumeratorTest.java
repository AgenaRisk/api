package com.agenarisk.test.learning;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regression.PartitionEnumerator;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

public class PartitionEnumeratorTest {

	{
		Environment.initialize();
	}

	private static final String MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"b1\","
			+ "        \"configuration\": {\"type\": \"Boolean\", \"states\": [\"False\", \"True\"]}"
			+ "      }, {"
			+ "        \"id\": \"cat\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"Low\", \"Mid\", \"High\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": []"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testEnumerationOrderMatchesEngineRowMajorConvention() throws Exception {
		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Node b1 = network.getNode("b1");
		Node cat = network.getNode("cat");

		// b1 first (slowest), cat second (fastest) - matches PartitionOrderingTest's confirmed convention
		List<PartitionEnumerator.Combination> combinations = PartitionEnumerator.enumerate(Arrays.asList(b1, cat));

		Assertions.assertEquals(6, combinations.size());
		Assertions.assertEquals("False", combinations.get(0).getState("b1"));
		Assertions.assertEquals("Low", combinations.get(0).getState("cat"));
		Assertions.assertEquals("False", combinations.get(1).getState("b1"));
		Assertions.assertEquals("Mid", combinations.get(1).getState("cat"));
		Assertions.assertEquals("False", combinations.get(2).getState("b1"));
		Assertions.assertEquals("High", combinations.get(2).getState("cat"));
		Assertions.assertEquals("True", combinations.get(3).getState("b1"));
		Assertions.assertEquals("Low", combinations.get(3).getState("cat"));
		Assertions.assertEquals("True", combinations.get(4).getState("b1"));
		Assertions.assertEquals("Mid", combinations.get(4).getState("cat"));
		Assertions.assertEquals("True", combinations.get(5).getState("b1"));
		Assertions.assertEquals("High", combinations.get(5).getState("cat"));
	}
}
