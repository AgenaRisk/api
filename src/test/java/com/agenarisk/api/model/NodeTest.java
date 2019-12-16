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
	
	/**
	 * Test for node.getAncestors() and node.getDescendants()
	 */
	@Test
	public void testGetAncestorsAndDescendants(){
		Model model = TestHelper.loadModelFromResource("/common/Node.testGetAncestorsAndDescendants.json");
		
		Network net1 = model.getNetwork("net1");
		Network net2 = model.getNetwork("net2");
		
		Node n1 = net1.getNode("n1");
		Node n2 = net1.getNode("n2");
		Node n3 = net1.getNode("n3");
		Node n4 = net1.getNode("n4");
		Node n5 = net1.getNode("n5");
		Node n6 = net1.getNode("n6");
		Node n7 = net1.getNode("n7");
		
		Node n2n1 = net2.getNode("n1");
		
		Assert.assertEquals(0, n1.getAncestors().size());
		Assert.assertEquals(6, n1.getDescendants().size());
		
		Assert.assertEquals(1, n2.getAncestors().size());
		Assert.assertEquals(4, n2.getDescendants().size());
		
		Assert.assertEquals(2, n3.getAncestors().size());
		Assert.assertEquals(2, n3.getDescendants().size());
		
		Assert.assertEquals(1, n4.getAncestors().size());
		Assert.assertEquals(3, n4.getDescendants().size());
		
		Assert.assertEquals(3, n5.getAncestors().size());
		Assert.assertEquals(2, n5.getDescendants().size());
		
		Assert.assertEquals(5, n6.getAncestors().size());
		Assert.assertEquals(1, n6.getDescendants().size());
		
		Assert.assertEquals(6, n7.getAncestors().size());
		Assert.assertEquals(0, n7.getDescendants().size());
		
		Assert.assertEquals(0, n2n1.getAncestors().size());
		Assert.assertEquals(0, n2n1.getDescendants().size());
	}
}
