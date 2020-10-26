package com.agenarisk.test.composite;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.Advisory;
import com.agenarisk.test.TestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class TestLoadWithTables {

	/**
	 * Testing that a model with Nodes that have incoming links loads successfully.
	 * <br>
	 * Specifically, we want to test against:
	 * <br>
	 * - Child node is found in data first
	 * <br>
	 * - It uses tokens in the expression that have not yet been registered
	 * 
	 * @throws Exception if something is wrong
	 */
	@Test
	public void testLoadWithTables() throws Exception {
		Model model = TestHelper.loadModelFromResource("/load/LoadWithTables.xml");
		model.calculate();
	}
	
	@Test
	public void testLoadInvalidTables() throws Exception {
		boolean error = false;
		try {
			Model model = TestHelper.loadModelFromResource("/load/LoadInvalidTableExpression.xml");
		}
		catch (Exception ex){
			error = true;
		}
		// We expect the model loading to fail without Advisory
		Assertions.assertEquals(true, error);
		
		// We now try to load with Advisory, which should succeed
		Advisory.getGroupByKey(this).linkToThread(Thread.currentThread());
		Model model = TestHelper.loadModelFromResource("/load/LoadInvalidTableExpression.xml");
		Assertions.assertEquals("Arithmetic(abc)",model.getNetworkList().get(0).getNode("nn1").getLogicNode().getExpression().toString());
		Assertions.assertEquals("Arithmetic(foo)",model.getNetworkList().get(0).getNode("nn2").getLogicNode().getPartitionedExpressions().get(0).toString());
		Assertions.assertEquals("Arithmetic(bar)",model.getNetworkList().get(0).getNode("nn2").getLogicNode().getPartitionedExpressions().get(1).toString());
	}
}
