package com.agenarisk.test.bcases;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Node;
import com.agenarisk.test.TestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class ARC225_222 {
	@Test
	/**
	 * Test that observations are not removed during static conversion
	 */
	public void testDefault() throws Exception {
		Model model = TestHelper.loadModelFromResource("/misc/ARC225_222.json");
		model.calculate();
		
		Node node1 = model.getNetwork("net1").getNode("original");
		Node node2 = model.getNetwork("net2").getNode("derivative");
		
		DataSet ds = model.getDataSetList().get(0);
		
		Assertions.assertEquals(15.3, node1.getLowerPercentileSetting());
		Assertions.assertEquals(25, node1.getUpperPercentileSetting());
		
		// We do not want to hard code any specific calculated value as those can change from release to release
		
		// Lower percentile of node1 is supposed to be passed to node2 as mean
		Assertions.assertEquals(ds.getCalculationResult(node1).getLowerPercentile(), ds.getCalculationResult(node2).getMean());
		
		// Upper percentile of node2 is supposed to be 25, so node2 is supposed to be lower than that
		Assertions.assertTrue(ds.getCalculationResult(node2).getMean() < ds.getCalculationResult(node1).getUpperPercentile());
	}
}
