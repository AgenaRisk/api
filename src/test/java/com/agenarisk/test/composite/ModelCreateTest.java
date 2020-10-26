package com.agenarisk.test.composite;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
		Assertions.assertEquals(ds, model.getDataSet("foo"));
		Assertions.assertEquals(model.getDataSet("foo"), model.getDataSetList().get(0));
		Assertions.assertEquals(1, model.getDataSetList().size());
		Assertions.assertEquals(1, model.getLogicModel().getScenarioList().getScenarios().size());
	}

}
