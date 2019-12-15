package com.agenarisk.api.model;

import com.agenarisk.test.TestHelper;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class NodeTest {
	
	/**
	 * Test for node.isConnectedInput() and node.isConnectedOutput()
	 */
	@Test
	public void testConnectedStatus() {
		Model model = TestHelper.loadModelFromResource("/common/Node.testConnectedStatus.json");
		
		Network net1 = model.getNetwork("net1");
		Network net2 = model.getNetwork("net2");
		
		Node n1n1 = net1.getNode("n1n1");
		Node n1n2 = net1.getNode("n1n2");
		Node n2n1 = net2.getNode("n2n1");
		Node n2n2 = net2.getNode("n2n2");
		Node n2n3 = net2.getNode("n2n3");
		
		Assert.assertEquals(true, n1n1.isConnectedOutput());
		Assert.assertEquals(false, n1n1.isConnectedInput());
		
		Assert.assertEquals(false, n1n2.isConnectedOutput());
		Assert.assertEquals(false, n1n2.isConnectedInput());
		
		Assert.assertEquals(false, n2n1.isConnectedOutput());
		Assert.assertEquals(true, n2n1.isConnectedInput());
		
		Assert.assertEquals(false, n2n2.isConnectedOutput());
		Assert.assertEquals(false, n2n2.isConnectedInput());
		
		Assert.assertEquals(false, n2n3.isConnectedOutput());
		Assert.assertEquals(false, n2n3.isConnectedInput());
	}
}
