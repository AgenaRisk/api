package com.agenarisk.test.composite;

import com.agenarisk.api.model.*;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 *
 * @author Eugene Dementiev
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CalculationOptions {
	
	Model model;
	Network sourceNet;
	Node sourceNode;
	Network targetNet;
	Node targetNode1;
	Node targetNode2;
	DataSet ds1;
	DataSet ds2;

	
	@BeforeEach
	public void setup(){
		model = Model.createModel();
		sourceNet = model.createNetwork("Net1");
		sourceNode = sourceNet.createNode("n11", Node.Type.ContinuousInterval);
		sourceNode.convertToSimulated();
		
		targetNet = model.createNetwork("Net2");
		targetNode1 = targetNet.createNode("n21", Node.Type.ContinuousInterval);
		targetNode1.convertToSimulated();
		targetNode2 = targetNet.createNode("n22", Node.Type.ContinuousInterval);
		targetNode2.convertToSimulated();
		Node.linkNodes(sourceNode, targetNode1, CrossNetworkLink.Type.Marginals);
		Node.linkNodes(targetNode1, targetNode2);
		targetNode2.setTableFunction("Arithmetic(n21)");
		
		ds1 = model.createDataSet("ds1");
		ds2 = model.createDataSet("ds2");
	}
	
	/**
	 * Just a basic run
	 */
	@Test
	public void testBasic() throws Exception {
		model.calculate();
		Assertions.assertTrue(ds1.getCalculationResults().size() > 0);
		Assertions.assertTrue(ds2.getCalculationResults().size() > 0);
		Assertions.assertEquals(ds1.getCalculationResult(sourceNode).getMean(), ds1.getCalculationResult(targetNode2).getMean());
	}
	
	/**
	 * Calculating all networks and just one dataset
	 */
	@Test
	public void testOneDataSet() throws Exception {
		model.calculate(null, Arrays.asList(ds1));
		Assertions.assertTrue(ds1.getCalculationResults().size() > 0);
		Assertions.assertTrue(ds2.getCalculationResults().isEmpty());
		Assertions.assertEquals(ds1.getCalculationResult(sourceNode).getMean(), ds1.getCalculationResult(targetNode2).getMean());
	}
	
	/**
	 * Calculating one network with ancestors and one dataset
	 */
	@Test
	public void testOneDataSetWAncestors() throws Exception {
		model.calculate(Arrays.asList(targetNet), Arrays.asList(ds1), Model.CalculationFlag.WITH_ANCESTORS);
		Assertions.assertNotNull(ds1.getCalculationResult(sourceNode));
		Assertions.assertNotNull(ds1.getCalculationResult(targetNode2));
		Assertions.assertEquals(ds1.getCalculationResult(sourceNode).getMean(), ds1.getCalculationResult(targetNode2).getMean());
	}
	
	/**
	 * 
	 */
	@Test
	public void testOneDataSetWOAncestors() throws Exception {
		model.calculate(Arrays.asList(targetNet), Arrays.asList(ds1));
		Assertions.assertNull(ds1.getCalculationResult(sourceNode));
		Assertions.assertNotNull(ds1.getCalculationResult(targetNode2));
	}
	
	/**
	 * Calculate one network with ancestors and one dataset with an observation in the ancestor
	 */
	@Test
	public void testOneDataSetWAncestorsWObservation() throws Exception {
		ds1.setObservation(sourceNode, 30);
		model.calculate(Arrays.asList(targetNet), Arrays.asList(ds1), Model.CalculationFlag.WITH_ANCESTORS);
		Assertions.assertNotNull(ds1.getCalculationResult(sourceNode));
		Assertions.assertNotNull(ds1.getCalculationResult(targetNode2));
		Assertions.assertEquals(30, ds1.getCalculationResult(targetNode1).getMean());
	}
	
	/**
	 * Calculate one network without ancestors with an observation in the ancestor which should have no effect on the final result
	 */
	@Test
	public void testOneDataSetWOAncestorsWObservation() throws Exception {
		ds1.setObservation(sourceNode, 30);
		model.calculate(Arrays.asList(targetNet), Arrays.asList(ds1));
		Assertions.assertEquals(0, ds1.getCalculationResult(targetNode1).getMean());
	}
	
}
