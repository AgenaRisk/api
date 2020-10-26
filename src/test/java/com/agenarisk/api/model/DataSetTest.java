package com.agenarisk.api.model;

import com.agenarisk.test.TestHelper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
/**
 *
 * @author Asu
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DataSetTest {
	
	Model model;
	DataSet dataSet;
	Network network;
	Node nodeCont;
	Node nodeDisc;
	
	@BeforeAll
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
	
	@Test
	public void testGetters() throws Exception {
		model = TestHelper.loadModelFromResource("/common/simpleABMultiplier.json");
		DataSet ds = model.createDataSet("ds");
		Network net = model.getNetworkList().get(0);
		Node a = net.getNode("A");
		Node b = net.getNode("B");
		
		ds.setObservation(a, 1);
		ds.setVariableObservation(b, "b_multiplier", 2);
		
		assertEquals(ds.getObservationsAndVariables().size(), 2);
		assertEquals(ds.getObservations().size(), 1);
		assertEquals(ds.getVariableObservations().size(), 1);
		assertEquals(ds.getObservationsAndVariables(a).size(), 1);
		assertNotNull(ds.getObservation(a));
		assertNull(ds.getObservation(b));
		assertEquals(ds.getObservationsAndVariables(b).size(), 1);
		assertEquals(ds.getVariableObservations(b).size(), 1);
		assertNotNull(ds.getVariableObservation(b, "b_multiplier"));
		
		ds.clearObservation(a);
		assertEquals(ds.getObservationsAndVariables().size(), 1);
		assertEquals(ds.getObservations().size(), 0);
		assertEquals(ds.getVariableObservations().size(), 1);
		assertEquals(ds.getObservationsAndVariables(a).size(), 0);
		assertNull(ds.getObservation(a));
		
		ds.setObservation(b, 5);
		assertEquals(ds.getObservationsAndVariables().size(), 2);
		assertEquals(ds.getObservations().size(), 1);
		assertEquals(ds.getVariableObservations().size(), 1);
		assertEquals(ds.getObservationsAndVariables(a).size(), 0);
		assertNull(ds.getObservation(a));
		assertNotNull(ds.getObservation(b));
		assertEquals(ds.getObservationsAndVariables(b).size(), 2);
		assertEquals(ds.getVariableObservations(b).size(), 1);
		
		ds.clearAllData();
		assertEquals(ds.getObservationsAndVariables().size(), 0);
		assertEquals(ds.getObservations().size(), 0);
		assertEquals(ds.getVariableObservations().size(), 0);
		assertEquals(ds.getObservationsAndVariables(a).size(), 0);
		assertNull(ds.getObservation(a));
		assertEquals(ds.getObservationsAndVariables(b).size(), 0);
		assertNull(ds.getObservation(b));
		assertNull(ds.getVariableObservation(b, "b_multiplier"));
		
		ds.setObservation(a, 1);
		ds.setVariableObservation(b, "b_multiplier", 2);
		ds.clearObservations();
		assertEquals(ds.getObservationsAndVariables().size(), 1);
		assertEquals(ds.getObservations().size(), 0);
		assertEquals(ds.getVariableObservations().size(), 1);
		assertEquals(ds.getObservationsAndVariables(a).size(), 0);
		assertEquals(ds.getObservationsAndVariables(b).size(), 1);
		
		ds.setObservation(a, 1);
		ds.setVariableObservation(b, "b_multiplier", 2);
		ds.clearVariableObservations();
		assertEquals(ds.getObservationsAndVariables().size(), 1);
		assertEquals(ds.getObservations().size(), 1);
		assertEquals(ds.getVariableObservations().size(), 0);
		assertEquals(ds.getObservationsAndVariables(a).size(), 1);
		assertEquals(ds.getObservationsAndVariables(b).size(), 0);
		
	}
	
}
