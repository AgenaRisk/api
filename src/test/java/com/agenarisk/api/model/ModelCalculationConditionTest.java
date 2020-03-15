package com.agenarisk.api.model;

import com.agenarisk.api.exception.CalculationException;
import org.junit.Test;

/**
 * Trivial tests for basic pre-calculation conditions
 * 
 * @author Eugene Dementiev
 */
public class ModelCalculationConditionTest {
	
	@Test(expected = CalculationException.class)
	public void testCalculateEmptyModel1() throws Exception {
		Model model = Model.createModel();
		model.calculate();
	}
	
	@Test(expected = CalculationException.class)
	public void testCalculateEmptyNetwork() throws Exception {
		Model model = Model.createModel();
		model.createNetwork("net");
		model.calculate();
	}
	
	@Test
	public void testCalculateModel() throws Exception {
		Model model = Model.createModel();
		Network net = model.createNetwork("net");
		net.createNode("node", Node.Type.Boolean);
		model.calculate();
	}

}
