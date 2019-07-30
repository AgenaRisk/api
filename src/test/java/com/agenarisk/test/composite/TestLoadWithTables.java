package com.agenarisk.test.composite;

import com.agenarisk.api.model.Model;
import com.agenarisk.test.TestHelper;
import org.junit.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class TestLoadWithTables {

	/**
	 * Testing that a model with Nodes that have incoming links loads successfully.
	 * <br>
	 * Specifically, we want to test against:
	 * <br>
	 * - Child node is found in data first
	 * <br>
	 * - It uses tokens in the expression that have not yet been registered
	 * 
	 * @throws Exception if something is wrong
	 */
	@Test
	public void testLoadWithTables() throws Exception {
		Model model = TestHelper.loadModelFromResource("/load/LoadWithTables.xml");
		model.calculate();
	}
	
}
