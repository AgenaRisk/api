package com.agenarisk.api.model;

import org.json.JSONObject;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class Settings {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		settings,
		iterations,
		convergence,
		tolerance,
		sampleSize,
		sampleSizeRanked,
		discreteTails,
		simulationLogging,
		parameterLearningLogging
	}
	
	public static void loadSettings(Model model, JSONObject jsonSettings) {
		if (jsonSettings == null){
			return;
		}
		uk.co.agena.minerva.model.Model logicModel = model.getLogicModel();
		logicModel.setSimulationNoOfIterations(jsonSettings.optInt(Field.iterations.toString(), logicModel.getSimulationNoOfIterations()));
		logicModel.setSimulationEntropyConvergenceTolerance(jsonSettings.optDouble(Field.convergence.toString(), logicModel.getSimulationEntropyConvergenceTolerance()));
		logicModel.setSimulationEvidenceTolerancePercent(jsonSettings.optDouble(Field.tolerance.toString(), logicModel.getSimulationEvidenceTolerancePercent()));
		logicModel.setSampleSize(jsonSettings.optInt(Field.sampleSize.toString(), logicModel.getSampleSize()));
		logicModel.setRankedSampleSize(jsonSettings.optInt(Field.sampleSizeRanked.toString(), logicModel.getRankedSampleSize()));
		logicModel.setSimulationTails(jsonSettings.optBoolean(Field.discreteTails.toString(), logicModel.isSimulationTails()));
		logicModel.setSimulationLogging(jsonSettings.optBoolean(Field.simulationLogging.toString(), logicModel.isSimulationLogging()));
		logicModel.setEMLogging(jsonSettings.optBoolean(Field.parameterLearningLogging.toString(), logicModel.isEMLogging()));
	}
	
}
