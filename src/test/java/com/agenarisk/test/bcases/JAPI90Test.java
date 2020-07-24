package com.agenarisk.test.bcases;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.test.TestHelper;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class JAPI90Test {
	
	@Test
	/**
	 * Test that observations are not removed during static conversion
	 */
	public void testDefault() throws Exception {
		Model model = TestHelper.loadModelFromResource("/misc/JAPI90.basic.json");
		DataSet ds = model.getDataSetList().get(0);

		int sizeBefore = ds.getObservationsAndVariables().size();
		model.calculate();
		model.convertToStatic(ds);
		int sizeAfter = ds.getObservationsAndVariables().size();
		
		Assert.assertTrue(sizeBefore > 0);
		Assert.assertEquals(sizeBefore, sizeAfter);
	}
}
