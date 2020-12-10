package com.agenarisk.api.model;

import com.agenarisk.api.exception.CalculationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 *
 * @author Eugene Dementiev
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VariableTest {
	
	Model model;
	DataSet ds;
	Network network1;
	Network network2;
	Node nodeA;
	Node nodeB;
	Node nodeC;

	@BeforeEach
	public void setupLocal() {
		model = Model.createModel();
		ds = model.createDataSet("ds");
		network1 = model.createNetwork("net1");
		network2 = model.createNetwork("net2");
		nodeA = network1.createNode("a", Node.Type.ContinuousInterval);
		nodeA.convertToSimulated();
		
		nodeB = network1.createNode("b", Node.Type.ContinuousInterval);
		nodeB.convertToSimulated();
		
		nodeC = network2.createNode("c", Node.Type.ContinuousInterval);
		nodeC.convertToSimulated();
	}
	
	@Test
	public void test1() throws Exception {
		
		Assertions.assertEquals(0, nodeA.getVariables().size());
		Assertions.assertEquals(0, nodeB.getVariables().size());
		Assertions.assertEquals(0, nodeC.getVariables().size());
		
		nodeA.createVariable("va1", 1);
		nodeA.createVariable("va2", 2);
		nodeA.setTableFunction("Arithmetic(va1+va2)");
		model.calculate();
		Assertions.assertEquals(3, ds.getCalculationResult(nodeA).getMean());
		
		nodeA.createVariable("va3", 4);
		nodeA.setTableFunction("Arithmetic(va1+va3)");
		model.calculate();
		Assertions.assertEquals(5, ds.getCalculationResult(nodeA).getMean());
		
		nodeA.setTableFunction("Arithmetic(va2)");
		model.calculate();
		Assertions.assertEquals(2, ds.getCalculationResult(nodeA).getMean());
		
		Assertions.assertEquals(3, nodeA.getVariables().size());
		
		nodeA.removeVariable("va1");
		Assertions.assertEquals(2, nodeA.getVariables().size());
		
		nodeA.removeVariable("va3");
		Assertions.assertEquals(1, nodeA.getVariables().size());
		
		model.calculate();
		Assertions.assertEquals(2, ds.getCalculationResult(nodeA).getMean());
		
		nodeA.getVariable("va2").setName("va20");
		nodeA.getVariable("va20").setValue(20);
		model.calculate();
		Assertions.assertEquals(20, ds.getCalculationResult(nodeA).getMean());
		
		nodeA.removeVariable("va2");
		// Old name should not exist
		Assertions.assertEquals(1, nodeA.getVariables().size());
		
		nodeA.removeVariable("va20");
		Assertions.assertEquals(0, nodeA.getVariables().size());
		
		Assertions.assertThrows(CalculationException.class, () -> {
			model.calculate();
		});
	}
	
	@Test
	public void testUpdatingSimple() throws Exception {
		nodeA.createVariable("va1", 1);
		nodeA.setTableFunction("Arithmetic(va1)");
		nodeA.getVariable("va1").setName("va2");
		nodeA.getVariable("va2").setValue(2);
		model.calculate();
		Assertions.assertEquals(2, ds.getCalculationResult(nodeA).getMean());
	}
	
	@Test
	public void testUpdatingCrossNetwork() throws Exception {
		nodeA.createVariable("va1", 1);
		nodeA.setTableFunction("Arithmetic(va1)");
		
		Node.linkNodes(nodeA, nodeC, CrossNetworkLink.Type.Marginals);

		model.calculate();
		Assertions.assertEquals(1, ds.getCalculationResult(nodeC).getMean());
		
		nodeA.getVariable("va1").setName("va2");
		nodeA.getVariable("va2").setValue(2);
		
		model.calculate();
		Assertions.assertEquals(2, ds.getCalculationResult(nodeC).getMean());
	}

}
