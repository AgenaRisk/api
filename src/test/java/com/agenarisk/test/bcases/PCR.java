package com.agenarisk.test.bcases;

import com.agenarisk.api.model.*;
import com.agenarisk.test.TestHelper;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class PCR {

	@Test
	/**
	 * Test that observations are not removed during static conversion
	 */
	public void testDefault() throws Exception {
		Model model = TestHelper.loadModelFromResource("/misc/pcr.json");

		model.calculate();
		Assertions.assertTrue(model.isCalculated());
		
		DataSet ds = model.getDataSetList().get(0);
		Network net = model.getNetworkList().get(0);
		Node node = net.getNode("Probability_have_disease_given_tested_positive");
		
		// The test is to make sure that marginals on this node are between 0 and 1
		List<ResultValue> dynStates = ds.getCalculationResult(node).getResultValues();
		Assertions.assertEquals("0.0", dynStates.get(0).getLabel().split(" - ")[0]);
		Assertions.assertEquals("1.0", dynStates.get(dynStates.size()-1).getLabel().split(" - ")[1]);
		
	}
}
