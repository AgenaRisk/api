package com.agenarisk.api.model;

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
		Model model = Model.createModel();
		Network net1 = model.createNetwork("net1");
		Network net2 = model.createNetwork("net2");
		Node n1n1 = net1.createNode("n1n1", Node.Type.Boolean);
		Node n1n2 = net1.createNode("n1n2", Node.Type.Boolean);
		
		Node n2n1 = net2.createNode("n2n1", Node.Type.Boolean);
		Node n2n2 = net2.createNode("n2n2", Node.Type.Boolean);
		Node n2n3 = net2.createNode("n2n3", Node.Type.Boolean);
		
		Node.linkNodes(n1n1, n1n2);
		Node.linkNodes(n1n1, n2n1, CrossNetworkLink.Type.Marginals);
		Node.linkNodes(n2n1, n2n2);
		Node.linkNodes(n2n1, n2n3);
		
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
