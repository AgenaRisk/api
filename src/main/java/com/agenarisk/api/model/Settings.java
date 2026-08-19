package com.agenarisk.api.model;

import com.agenarisk.api.model.interfaces.Storable;
import org.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;

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
		sampleSizeRanked,
		simulationLogging,
		parameterLearningLogging,
		splitMetric
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
		loadSettings(logicModel, jsonSettings);
	}
	
	/**
	 * Utility method to load settings from the provided JSON to the provided API1 Model.<br>
	 * For any missing fields, current model settings (or defaults) will be used.
	 * 
	 * @param logicModel API1 Model to load settings to
	 * @param jsonSettings JSON to load settings from
	 */
	public static void loadSettings(uk.co.agena.minerva.model.Model logicModel, JSONObject jsonSettings){
		if (jsonSettings == null){
			return;
		}
		logicModel.setSimulationNoOfIterations(jsonSettings.optInt(Field.iterations.toString(), logicModel.getSimulationNoOfIterations()));
		logicModel.setSimulationEntropyConvergenceTolerance(jsonSettings.optDouble(Field.convergence.toString(), logicModel.getSimulationEntropyConvergenceTolerance()));
		logicModel.setSimulationEvidenceTolerancePercent(jsonSettings.optDouble(Field.tolerance.toString(), logicModel.getSimulationEvidenceTolerancePercent()));
		logicModel.setRankedSampleSize(jsonSettings.optInt(Field.sampleSizeRanked.toString(), logicModel.getRankedSampleSize()));
		logicModel.setSplitMetric(jsonSettings.optString(Field.splitMetric.toString(), logicModel.getSplitMetric()));
		logicModel.setSimulationLogging(jsonSettings.optBoolean(Field.simulationLogging.toString(), logicModel.isSimulationLogging()));
		logicModel.setEMLogging(jsonSettings.optBoolean(Field.parameterLearningLogging.toString(), logicModel.isEMLogging()));
	}
	
	/**
	 * Utility method to load per-network settings from the provided JSON onto the provided API1 Network.<br>
	 * Only the fields actually present in the JSON become overrides; any other field is left to inherit
	 * the model-level setting, which is what every model without a network settings block does.<br>
	 * Note that only a subset of the model-level fields is supported per network: sample size for ranked
	 * nodes is applied during NPT generation rather than per network, and the logging flags write to a
	 * single shared log.
	 *
	 * @param ebn API1 Network to load settings onto
	 * @param jsonSettings JSON to load settings from; null or empty clears all overrides
	 */
	public static void loadSettings(ExtendedBN ebn, JSONObject jsonSettings){
		if (ebn == null){
			return;
		}

		ebn.clearSimulationSettingOverrides();

		if (jsonSettings == null){
			return;
		}

		if (jsonSettings.has(Field.iterations.toString())){
			ebn.setSimulationNoOfIterationsOverride(jsonSettings.getInt(Field.iterations.toString()));
		}
		if (jsonSettings.has(Field.convergence.toString())){
			ebn.setSimulationEntropyConvergenceToleranceOverride(jsonSettings.getDouble(Field.convergence.toString()));
		}
		if (jsonSettings.has(Field.tolerance.toString())){
			ebn.setSimulationEvidenceTolerancePercentOverride(jsonSettings.getDouble(Field.tolerance.toString()));
		}
		if (jsonSettings.has(Field.splitMetric.toString())){
			ebn.setSplitMetricOverride(jsonSettings.getString(Field.splitMetric.toString()));
		}
	}

	/**
	 * Utility method to build a JSON equivalent of the per-network settings of the provided API1 Network.
	 *
	 * @param ebn API1 Network
	 *
	 * @return JSON equivalent of the network's setting overrides, or null if the network overrides nothing
	 */
	public static JSONObject toJson(ExtendedBN ebn) {
		if (ebn == null || !ebn.hasSimulationSettingOverrides()){
			return null;
		}

		JSONObject jsonSettings = new JSONObject();
		if (ebn.getSimulationNoOfIterationsOverride() != null){
			jsonSettings.put(Field.iterations.toString(), ebn.getSimulationNoOfIterationsOverride().intValue());
		}
		if (ebn.getSimulationEntropyConvergenceToleranceOverride() != null){
			jsonSettings.put(Field.convergence.toString(), ebn.getSimulationEntropyConvergenceToleranceOverride().doubleValue());
		}
		if (ebn.getSimulationEvidenceTolerancePercentOverride() != null){
			jsonSettings.put(Field.tolerance.toString(), ebn.getSimulationEvidenceTolerancePercentOverride().doubleValue());
		}
		if (ebn.getSplitMetricOverride() != null){
			jsonSettings.put(Field.splitMetric.toString(), ebn.getSplitMetricOverride());
		}
		return jsonSettings;
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
		jsonSettings.put(Settings.Field.sampleSizeRanked.toString(), model.getRankedSampleSize());
		jsonSettings.put(Settings.Field.splitMetric.toString(), model.getSplitMetric());
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
		model.getLogicModel().setSimulationEvidenceTolerancePercent(tolerance);
	}

	/**
	 * Gets ranked node sample size.
	 * 
	 * @return ranked node sample size
	 */
	public int getSampleSize() {
		return model.getLogicModel().getRankedSampleSize();
	}

	/**
	 * Sets ranked node sample size.
	 * 
	 * @param sampleSize ranked node sample size
	 */
	public void setSampleSize(int sampleSize) {
		model.getLogicModel().setRankedSampleSize(sampleSize);
	}

	/**
	 * Returns the dynamic-discretisation split metric in force for this model.
	 *
	 * @return {@code entropy} (classic) or {@code entropyVarianceLeverage}
	 */
	public String getSplitMetric() {
		return model.getLogicModel().getSplitMetric();
	}

	/**
	 * Sets the dynamic-discretisation split metric. Anything unrecognised - including null - selects
	 * the classic entropy metric, so an unexpected value can never silently change results.
	 *
	 * @param splitMetric {@code entropy} or {@code entropyVarianceLeverage}
	 */
	public void setSplitMetric(String splitMetric) {
		model.getLogicModel().setSplitMetric(splitMetric);
	}

	/**
	 * Checks whether tails are discretized during simulated calculation.
	 * 
	 * @return always false. The percentile tail-split pass was removed from the engine.
	 * @deprecated retained for source compatibility; has no effect
	 */
	@Deprecated
	public boolean isDiscretizeTails() {
		return model.getLogicModel().isSimulationTails();
	}

	/**
	 * Sets whether tails are discretized during simulated calculation.
	 * 
	 * @param discretizeTails ignored. The percentile tail-split pass was removed from the engine.
	 * @deprecated retained for source compatibility; has no effect
	 */
	@Deprecated
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
