package com.agenarisk.api.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 *
 * @author Eugene Dementiev
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SettingsTest {
	
	Model model;

	@BeforeAll
	public void setup(){
		model = Model.createModel();
	}
	
	@Test
	public void testIterations(){
		for(int iter: new int[]{50, 10, 70, 100}){
			model.getSettings().setIterations(iter);
			Assertions.assertEquals(iter, model.getSettings().getIterations());
			Assertions.assertEquals(iter, model.getLogicModel().getSimulationNoOfIterations());
		}
	}

	@Test
	public void testConvergence(){
		for(double conv: new double[]{0.1, 0.001, 0.0001, 0.00001}){
			model.getSettings().setConvergence(conv);
			Assertions.assertEquals(conv, model.getSettings().getConvergence());
			Assertions.assertEquals(conv, model.getLogicModel().getSimulationEntropyConvergenceTolerance());
		}
	}

	@Test
	public void testTolerance(){
		for(double toler: new double[]{1, 1.5, 2, 2.5, 10, 20}){
			model.getSettings().setTolerance(toler);
			Assertions.assertEquals(toler, model.getSettings().getTolerance());
			Assertions.assertEquals(toler, model.getLogicModel().getSimulationEvidenceTolerancePercent());
		}
	}

	@Test
	public void testSampleSize(){
		for(int sampls: new int[]{3, 4, 5, 6}){
			model.getSettings().setSampleSize(sampls);
			Assertions.assertEquals(sampls, model.getSettings().getSampleSize());
			Assertions.assertEquals(sampls, model.getLogicModel().getRankedSampleSize());
		}
	}

	/**
	 * The percentile tail-split pass was removed from the engine, and
	 * Model.setSimulationTails/isSimulationTails are deprecated no-ops: the setter does nothing and
	 * the getter always returns false. Settings keeps its accessors so existing callers still
	 * compile. This pins that contract rather than the round-trip the feature used to provide, so a
	 * change that silently revived the field would still be caught.
	 */
	@Test
	public void testSimulationTailsIsANoOp(){
		for(boolean discr: new boolean[]{true, false, true}){
			model.getSettings().setDiscretizeTails(discr);
			Assertions.assertFalse(model.getSettings().isDiscretizeTails());
			Assertions.assertFalse(model.getLogicModel().isSimulationTails());
		}
	}

}
