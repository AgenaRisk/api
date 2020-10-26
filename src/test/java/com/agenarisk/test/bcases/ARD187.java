package com.agenarisk.test.bcases;

import com.agenarisk.api.model.*;
import com.agenarisk.test.TestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class ARD187 {
	
	@Test
	public void test(){
		Model model = TestHelper.loadModelFromResource("/misc/ARD187.cmpx");
		Network net = model.getNetworkList().get(0);
		Node observedNode = net.getNode("False_negative_rate");
		
		DataSet ds = model.getDataSetList().get(0);
		
		Assertions.assertDoesNotThrow(() -> {
			ds.setObservation(observedNode, 0);
			model.calculate();
		});
		
		Assertions.assertDoesNotThrow(() -> {
			ds.setObservation(observedNode, 0.1);
			model.calculate();
		});
		
		Assertions.assertDoesNotThrow(() -> {
			ds.setObservation(observedNode, 0.5);
			model.calculate();
		});
		
		Assertions.assertDoesNotThrow(() -> {
			ds.setObservation(observedNode, 1);
			model.calculate();
		});

	}
}
