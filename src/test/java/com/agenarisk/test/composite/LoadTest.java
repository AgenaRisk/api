package com.agenarisk.test.composite;

import com.agenarisk.api.model.Model;
import com.agenarisk.test.TestHelper;
import java.nio.file.Path;
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
	
	/**
	 * This test makes sure that this API can load and calculate an AgenaRisk 7 CMP model
	 */
	@Test
	public void testLoadCMP() throws Exception {
		Path tempPath = TestHelper.tempFileCopyOfResource("/load/ar7-asia.cmp");
		Model model = Model.loadModel(tempPath.toString());
		model.calculate();
	}
}
