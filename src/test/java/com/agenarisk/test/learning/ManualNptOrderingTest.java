package com.agenarisk.test.learning;

import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Empirically determines the row/column convention and parent-combination enumeration order for
 * {@code Node.setTableColumns}/{@code setTableRows}, since this is a different code path from partitioned
 * expressions (it goes through {@code getParents()}, a LinkedHashSet built from link insertion order, and
 * {@code ExtendedNode.setNPT} directly rather than the functionMode/partitioned-expression machinery).
 */
public class ManualNptOrderingTest {

	{
		Environment.initialize();
	}

	private static final String MODEL_JSON = "{"
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
			+ "        \"id\": \"c\","
			+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"S0\", \"S1\", \"S2\"]}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": ["
			+ "      {\"parent\": \"p1\", \"child\": \"c\"},"
			+ "      {\"parent\": \"p2\", \"child\": \"c\"}"
			+ "    ]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testParentOrderMatchesLinkInsertionOrder() throws Exception {
		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Node p1 = network.getNode("p1");
		Node p2 = network.getNode("p2");
		Node c = network.getNode("c");

		List<Node> parents = new ArrayList<>(c.getParents());
		Assertions.assertEquals(2, parents.size());
		Assertions.assertEquals("p1", parents.get(0).getId());
		Assertions.assertEquals("p2", parents.get(1).getId());
	}

	@Test
	public void testColumnMajorRowMajorCombinationOrder() throws Exception {
		Model model = Model.createModel(new JSONObject(MODEL_JSON));
		Network network = model.getNetworkList().get(0);
		Node p1 = network.getNode("p1");
		Node p2 = network.getNode("p2");
		Node c = network.getNode("c");

		// 4 combinations (p1 x p2), 3 states each for c. Give each combination a distinct, identifiable
		// one-hot-ish distribution so we can read back which combination landed where.
		// Combination order hypothesis: p1 slowest, p2 fastest (same as partitioned expressions):
		// combo0 = (False,False), combo1 = (False,True), combo2 = (True,False), combo3 = (True,True)
		double[][] columns = {
			{1.0, 0.0, 0.0}, // combo 0 -> S0
			{0.0, 1.0, 0.0}, // combo 1 -> S1
			{0.0, 0.0, 1.0}, // combo 2 -> S2
			{0.5, 0.5, 0.0}  // combo 3 -> S0/S1 mix
		};
		c.setTableColumns(columns);

		Assertions.assertEquals(1.0, probabilityOf(model, p1, "False", p2, "False", c, "S0"), 1e-9);
		Assertions.assertEquals(1.0, probabilityOf(model, p1, "False", p2, "True", c, "S1"), 1e-9);
		Assertions.assertEquals(1.0, probabilityOf(model, p1, "True", p2, "False", c, "S2"), 1e-9);
		Assertions.assertEquals(0.5, probabilityOf(model, p1, "True", p2, "True", c, "S0"), 1e-9);
		Assertions.assertEquals(0.5, probabilityOf(model, p1, "True", p2, "True", c, "S1"), 1e-9);
	}

	private double probabilityOf(Model model, Node p1, String s1, Node p2, String s2, Node c, String targetState) throws Exception {
		DataSet dataSet = model.getDataSetList().isEmpty() ? model.createDataSet("ds") : model.getDataSetList().get(0);
		dataSet.clearObservations();
		dataSet.setObservationHard(p1, s1);
		dataSet.setObservationHard(p2, s2);
		model.calculate();
		CalculationResult cr = dataSet.getCalculationResult(c);
		return cr.getResultValue(targetState).getValue();
	}
}
