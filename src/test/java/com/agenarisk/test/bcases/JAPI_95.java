package com.agenarisk.test.bcases;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class JAPI_95 {
	@Test
	/**
	 * Test that no exceptions are thrown if trying to retrieve results when some but not all networks were not calculated
	 */
	public void testDefault() throws Exception {
		Model model = Model.createModel();
		DataSet ds = model.createDataSet("ds");
		Network net1 = model.createNetwork("net1");
		Network net2 = model.createNetwork("net2");
		Node node1 = net1.createNode("a", Node.Type.Ranked);
		Node node2 = net2.createNode("a", Node.Type.Ranked);
		model.calculate(Arrays.asList(net1), null);
		Assertions.assertTrue(!ds.getCalculationResults().isEmpty());
	}
}
