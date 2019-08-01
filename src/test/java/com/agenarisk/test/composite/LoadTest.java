package com.agenarisk.test.composite;

import com.agenarisk.test.TestHelper;
import org.junit.Test;

/**
 * 
 * @author Eugene Dementiev
 */
public class LoadTest {
	
	/**
	 * This test makes sure that mismatching dynamic variables in an input node do not prevent loading
	 */
	@Test
	public void testLoadInputNodeTable() throws Exception {
		TestHelper.loadModelFromResource("/load/LoadInputNodeTable.xml").calculate();
		
	}
}
