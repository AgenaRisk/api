package com.agenarisk.api.model;

import com.agenarisk.test.TestHelper;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Asu
 */
public class DataSetTest {
	
	public DataSetTest() {
	}
	
	Model model;
	DataSet dataSet;
	Network network;
	Node nodeCont;
	Node nodeDisc;
	
	@Before
	public void loadCarExample(){
		model = TestHelper.loadModelFromResource("/common/Biased Coin Flip Experiment.cmpx");
		network = model.getNetworkList().get(0);
		dataSet = model.getDataSetList().get(0);
		nodeCont = network.getNode("p_heads");
		nodeDisc = network.getNode("Type");
	}

	/**
	 * Test of createDataSet method, of class DataSet.
	 */
	@Test
	public void testCreateDataSet_Model_String() {
	}

	/**
	 * Test of createDataSet method, of class DataSet.
	 */
	@Test
	public void testCreateDataSet_Model_JSONObject() throws Exception {
	}

	/**
	 * Test of getLogicScenario method, of class DataSet.
	 */
	@Test
	public void testGetLogicScenario() {
	}

	/**
	 * Test of setLogicScenario method, of class DataSet.
	 */
	@Test
	public void testSetLogicScenario() {
	}

	/**
	 * Test of getModel method, of class DataSet.
	 */
	@Test
	public void testGetModel() {
	}

	/**
	 * Test of getId method, of class DataSet.
	 */
	@Test
	public void testGetId() {
	}

	/**
	 * Test of setId method, of class DataSet.
	 */
	@Test
	public void testSetId() throws Exception {
	}

	/**
	 * Test of setObservationHard method, of class DataSet.
	 */
	@Test
	public void testSetObservationHard_Node_int() throws Exception {
	}

	/**
	 * Test of setObservationHard method, of class DataSet.
	 */
	@Test
	public void testSetObservationHard_Node_double() throws Exception {
	}

	/**
	 * Test of setObservationHard method, of class DataSet.
	 */
	@Test
	public void testSetObservationHard_Node_String() throws Exception {
	}

	/**
	 * Test of setObservationConstant method, of class DataSet.
	 */
	@Test
	public void testSetObservationConstant() throws Exception {
	}

	/**
	 * Test of setObservationHardGeneric method, of class DataSet.
	 */
	@Test
	public void testSetObservationHardGeneric() throws Exception {
	}

	/**
	 * Test of setObservationSoft method, of class DataSet.
	 */
	@Test
	public void testSetObservationSoft_3args() throws Exception {
	}

	/**
	 * Test of setObservationSoft method, of class DataSet.
	 */
	@Test
	public void testSetObservationSoft_Node_Map() throws Exception {
	}

	/**
	 * Test of setObservation method, of class DataSet.
	 */
	@Test
	public void testSetObservation() throws Exception {
	}

	/**
	 * Test of clearObservation method, of class DataSet.
	 */
	@Test
	public void testClearObservation() {
	}

	/**
	 * Test of clearObservations method, of class DataSet.
	 */
	@Test
	public void testClearObservations() {
	}

	/**
	 * Test of hasObservation method, of class DataSet.
	 */
	@Test
	public void testHasObservation() {
	}

	/**
	 * Test of getObservation method, of class DataSet.
	 */
	@Test
	public void testGetObservation() {
	}

	/**
	 * Test of getCalculationResult method, of class DataSet.
	 */
	@Test
	public void testGetCalculationResult() throws Exception {
	}

	/**
	 * Test of getCalculationResults method, of class DataSet.
	 */
	@Test
	public void testGetCalculationResults_0args() {
		assertEquals(dataSet.getCalculationResults().size(), dataSet.getCalculationResults(network).size());
		assertEquals(dataSet.getCalculationResult(nodeDisc).getResultValues().size(), 3);
		assertEquals(dataSet.getCalculationResult(nodeCont).getResultValues().size(), 24);
	}

	/**
	 * Test of getCalculationResults method, of class DataSet.
	 */
	@Test
	public void testGetCalculationResults_Network() {
		assertEquals(dataSet.getCalculationResults(network).size(), 4);
	}

	/**
	 * Test of loadCalculationResult method, of class DataSet.
	 */
	@Test
	public void testLoadCalculationResult() throws Exception {
	}

	/**
	 * Test of getDataSetIndex method, of class DataSet.
	 */
	@Test
	public void testGetDataSetIndex() {
	}
	
}
