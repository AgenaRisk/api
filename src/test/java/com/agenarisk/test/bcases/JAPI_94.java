package com.agenarisk.test.bcases;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class JAPI_94 {
	@Test
	/**
	 * Test that no exceptions are thrown if trying to retrieve results before any calculations
	 */
	public void testDefault() throws Exception {
		Model model = Model.createModel();
		DataSet ds = model.createDataSet("ds");
		Network net = model.createNetwork("net");
		Node node = net.createNode("a", Node.Type.Ranked);
		Assertions.assertTrue(ds.getCalculationResults().isEmpty());
		model.calculate();
		Assertions.assertTrue(!ds.getCalculationResults().isEmpty());
	}
}
