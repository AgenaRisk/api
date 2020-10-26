package com.agenarisk.test.bcases;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 *
 * @author Eugene Dementiev
 */
@TestInstance(Lifecycle.PER_CLASS)
public class ARD157Test {
	
	Model model;
	Network net;
	Node x, y, n1, n2, n3;
	DataSet ds;
	@BeforeAll
	public void prepare(){
		model = Model.createModel();
		net = model.createNetwork("net");
		x = net.createNode("x", Node.Type.ContinuousInterval);
		x.convertToSimulated();
		y = net.createNode("y", Node.Type.ContinuousInterval);
		y.convertToSimulated();
		
		n1 = net.createNode("n1", Node.Type.Boolean);
		n1.linkFrom(x);
		n1.linkFrom(y);
		n1.setTableFunction("Comparative(if(x>y||x==y,\"True\",\"False\"))");
		
		n2 = net.createNode("n2", Node.Type.Boolean);
		n2.linkFrom(x);
		n2.linkFrom(y);
		n2.setTableFunction("Comparative(if(x>=y,\"True\",\"False\"))");
		
		n3 = net.createNode("n3", Node.Type.Boolean);
		n3.linkFrom(x);
		n3.linkFrom(y);
		n3.setTableFunction("Comparative(if(x>y,\"True\",\"False\"))");
		
		ds = model.createDataSet("ds");
		ds.setObservation(x, 21);
		ds.setObservation(y, 20);
	}
	
	@Test
	/**
	 * Test with x and y default expressions
	 */
	public void testDefault() throws Exception {
		model.calculate();
		Assertions.assertEquals(1, ds.getCalculationResult(n1).getResultValue("True").getValue(), 0);
		Assertions.assertEquals(1, ds.getCalculationResult(n2).getResultValue("True").getValue(), 0);
		Assertions.assertEquals(1, ds.getCalculationResult(n3).getResultValue("True").getValue(), 0);
	}
	
	@Test
	/**
	 * Test with x and y Arithmetic(0)
	 */
	public void testArithmetic() throws Exception{
		x.setTableFunction("Arithmetic(0)");
		y.setTableFunction("Arithmetic(0)");
		model.calculate();
		Assertions.assertEquals(1, ds.getCalculationResult(n1).getResultValue("True").getValue(), 0);
		Assertions.assertEquals(1, ds.getCalculationResult(n2).getResultValue("True").getValue(), 0);
		Assertions.assertEquals(1, ds.getCalculationResult(n3).getResultValue("True").getValue(), 0);
	}
	
	@Test
	/**
	 * Test with x and y Uniform(0.0,100.0)
	 */
	public void testUniform() throws Exception{
		x.setTableFunction("Uniform(0.0,100.0)");
		y.setTableFunction("Uniform(0.0,100.0)");
		model.calculate();
		Assertions.assertEquals(1, ds.getCalculationResult(n1).getResultValue("True").getValue(), 0);
		Assertions.assertEquals(1, ds.getCalculationResult(n2).getResultValue("True").getValue(), 0);
		Assertions.assertEquals(1, ds.getCalculationResult(n3).getResultValue("True").getValue(), 0);
	}
	
}
