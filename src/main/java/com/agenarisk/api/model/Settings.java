package com.agenarisk.api.model;

import com.agenarisk.api.model.interfaces.Storable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is a class for Model calculation settings.
 * 
 * @author Eugene Dementiev
 */
public class Settings implements Storable {
	
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
	
	/**
	 * Utility method to load settings from the provided JSON to the provided Model.<br>
	 * For any missing fields, current model settings (or defaults) will be used.
	 * 
	 * @param model Model to load settings to
	 * @param jsonSettings JSON to load settings from
	 */
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
	
	/**
	 * Utility method to build a JSON equivalent of settings from the provided API1 model
	 * 
	 * @param model API1 model
	 * 
	 * @return JSON equivalent of the Settings
	 */
	public static JSONObject toJson(uk.co.agena.minerva.model.Model model) {
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
	
	private final Model model;
	
	protected Settings(Model model){
		this.model = model;
	}

	/**
	 * Returns maximum number of iterations during model calculation.
	 * 
	 * @return maximum number of iterations during model calculation
	 */
	public int getIterations() {
		return model.getLogicModel().getSimulationNoOfIterations();
	}

	/**
	 * Sets maximum number of iterations during model calculation.
	 * 
	 * @param iterations maximum number of iterations during model calculation
	 */
	public void setIterations(int iterations) {
		model.getLogicModel().setSimulationNoOfIterations(iterations);
	}

	/**
	 * Gets simulation entropy error convergence threshold.
	 * 
	 * @return simulation entropy error convergence threshold
	 */
	public double getConvergence() {
		return model.getLogicModel().getSimulationEntropyConvergenceTolerance();
	}

	/**
	 * Sets simulation entropy error convergence threshold
	 * 
	 * @param convergence simulation entropy error convergence threshold
	 */
	public void setConvergence(double convergence) {
		model.getLogicModel().setSimulationEntropyConvergenceTolerance(convergence);
	}

	/**
	 * Gets simulation evidence tolerance percent.
	 * 
	 * @return simulation evidence tolerance percent
	 */
	public double getTolerance() {
		return model.getLogicModel().getSimulationEvidenceTolerancePercent();
	}

	/**
	 * Sets simulation evidence tolerance percent.
	 * 
	 * @param tolerance simulation evidence tolerance percent
	 */
	public void setTolerance(double tolerance) {
		model.getLogicModel().setSimulationEntropyConvergenceTolerance(tolerance);
	}

	/**
	 * Gets ranked node sample size.
	 * 
	 * @return ranked node sample size
	 */
	public int getSampleSize() {
		return model.getLogicModel().getSampleSize();
	}

	/**
	 * Sets ranked node sample size.
	 * 
	 * @param sampleSize ranked node sample size
	 */
	public void setSampleSize(int sampleSize) {
		model.getLogicModel().setSampleSize(sampleSize);
	}

	/**
	 * Checks whether tails are discretized during simulated calculation.
	 * 
	 * @return true if tails are discretized during simulated calculation, false otherwise
	 */
	public boolean isDiscretizeTails() {
		return model.getLogicModel().isSimulationTails();
	}

	/**
	 * Sets whether tails are discretized during simulated calculation.
	 * 
	 * @param discretizeTails whether tails are discretized during simulated calculation
	 */
	public void setDiscretizeTails(boolean discretizeTails) {
		model.getLogicModel().setSimulationTails(discretizeTails);
	}
	
	/**
	 * Returns a JSON representation of the Model settings.
	 * 
	 * @return JSONObject equivalent of Model settings
	 */
	@Override
	public JSONObject toJson() {
		return toJson(model.getLogicModel());
	}
	
	/**
	 * Applies Model settings from JSON.
	 * 
	 * @param jsonSettings JSONObject equivalent of Model settings
	 */
	public void fromJson(JSONObject jsonSettings){
		loadSettings(model, jsonSettings);
	}

}
