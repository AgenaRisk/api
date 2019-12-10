package com.agenarisk.api.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is a stub class for Model calculation settings.
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
	
	public static JSONObject toJson(uk.co.agena.minerva.model.Model model) throws JSONException {
		JSONObject jsonSettings = new JSONObject();
		jsonSettings.put(Settings.Field.iterations.toString(), model.getSimulationNoOfIterations());
		jsonSettings.put(Settings.Field.convergence.toString(), model.getSimulationEntropyConvergenceTolerance());
		jsonSettings.put(Settings.Field.tolerance.toString(), model.getSimulationEvidenceTolerancePercent());
		jsonSettings.put(Settings.Field.sampleSize.toString(), model.getSampleSize());
		jsonSettings.put(Settings.Field.sampleSizeRanked.toString(), model.getRankedSampleSize());
		jsonSettings.put(Settings.Field.discreteTails.toString(), model.isSimulationTails());
		jsonSettings.put(Settings.Field.simulationLogging.toString(), model.isSimulationLogging());
		jsonSettings.put(Settings.Field.parameterLearningLogging.toString(), model.isEMLogging());
		return jsonSettings;
	}
	
}
