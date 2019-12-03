package com.agenarisk.test.composite;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class ModelCreateTest {
	
	/**
	 * Testing that DataSet iteration works for new models
	 */
	@Test
	public void testCreateModel(){
		Model model = Model.createModel();
		DataSet ds = model.createDataSet("foo");
		Assert.assertEquals(ds, model.getDataSet("foo"));
		Assert.assertEquals(model.getDataSet("foo"), model.getDataSetList().get(0));
		Assert.assertEquals(1, model.getDataSetList().size());
		Assert.assertEquals(1, model.getLogicModel().getScenarioList().getScenarios().size());
	}

}
