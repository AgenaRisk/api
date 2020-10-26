package com.agenarisk.test.composite;

import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.model.Model;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * 
 * @author Eugene Dementiev
 */
public class CalculationTest {
	
	{
		Environment.initialize();
	}
	
	@Test
	public void testCalcFail() throws Exception {
		Path dirPath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("calc/fail");
		Files.list(dirPath).forEach(path -> {
			
			Model model;
			try {
				model = Model.loadModel(path.toString());
			}
			catch (ModelException ex){
				// Model must load correctly
				throw new RuntimeException(ex);
			}
			
			boolean error = false;
			try {
				model.calculate();
			}
			catch (CalculationException ex){
				error = true;
			}
			
			Assertions.assertTrue(error);
		});
	}
	
	@Test
	public void testCalcOk() throws Exception {
		Path dirPath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("calc/ok");
		Files.list(dirPath).forEach(path -> {
			
			Model model;
			try {
				model = Model.loadModel(path.toString());
			}
			catch (ModelException ex){
				// Model must load correctly
				throw new RuntimeException(ex);
			}
			
			try {
				model.calculate();
			}
			catch (CalculationException ex){
				throw new RuntimeException(ex);
			}
			
		});
	}
}
