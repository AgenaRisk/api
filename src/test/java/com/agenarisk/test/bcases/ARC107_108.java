package com.agenarisk.test.bcases;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import com.agenarisk.test.TestHelper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class ARC107_108 {

	@Test
	/**
	 * Test that observations are not removed during static conversion
	 */
	public void testDefault() throws Exception {
		Model model = TestHelper.loadModelFromResource("/misc/ARC107_108.basic.cmpx");
		DataSet ds = model.getDataSetList().get(0);

		// Verify that observations are kept
		int sizeBefore = ds.getObservationsAndVariables().size();
		model.calculate(null, Arrays.asList(ds), Model.CalculationFlag.KEEP_TAILS_ZERO_REGIONS);
		model.convertToStatic(ds);
		int sizeAfter = ds.getObservationsAndVariables().size();

		Assertions.assertTrue(sizeBefore > 0);
		Assertions.assertEquals(sizeBefore, sizeAfter);
		
		// Verify that all nodes have infinite bounds
		for(Node node: model.getNetworkList().get(0).getNodeList()){
			List<State> states = node.getStates();
			Double lowerBound = states.get(0).getLogicState().getRange().getLowerBound();
			Double upperBound = states.get(states.size()-1).getLogicState().getRange().getUpperBound();
			System.out.println(node.toStringExtra());
			System.out.println(Double.isInfinite(lowerBound));
			System.out.println(Double.isInfinite(upperBound));
		}
		
		model.calculate();
		
		Assertions.assertTrue(model.isCalculated());
	}
}
