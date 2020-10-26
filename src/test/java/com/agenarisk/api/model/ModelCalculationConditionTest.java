package com.agenarisk.api.model;

import com.agenarisk.api.exception.CalculationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Trivial tests for basic pre-calculation conditions
 *
 * @author Eugene Dementiev
 */
public class ModelCalculationConditionTest {

	@Test()
	public void testCalculateEmptyModel1() throws Exception {
		Assertions.assertThrows(CalculationException.class, () -> {
			Model model = Model.createModel();
			model.calculate();
		});
	}

	@Test()
	public void testCalculateEmptyNetwork() throws Exception {
		Assertions.assertThrows(CalculationException.class, () -> {
			Model model = Model.createModel();
			model.createNetwork("net");
			model.calculate();
		});
	}

	@Test
	public void testCalculateModel() throws Exception {
		Model model = Model.createModel();
		Network net = model.createNetwork("net");
		net.createNode("node", Node.Type.Boolean);
		model.calculate();
	}

}
