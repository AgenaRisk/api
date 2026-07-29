package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class EMParameterLearningConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<EMParameterLearningConfigurer> {
	
	private Path dataPath;
	private Path modelPath;
	private String modelStageLabel;
	private String modelPrefix;
	private Model model;
	private String missingValue = "";
	private String valueSeparator = ",";
	private int maxIterations = 50;
	private double convergenceThreshold = 0.01;
	
	private double dataWeight = 1;
	private final HashMap<String, Double> nodeDataWeightsCustom = new HashMap<>();
	private String nodeLabel = "";
	private boolean progressEnabled = false;
	
	public EMParameterLearningConfigurer(Config config) {
		super(config);
	}
	
	public EMParameterLearningConfigurer() {
		super();
	}
	
	@Override
	public EMParameterLearningConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());
		if (jParameters.has("dataPath")){
			dataPath = Paths.get(jParameters.getString("dataPath"));
			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());
			config.setPathInput(dataPath.toString());
		}
		else {
			dataPath = config.getPathInput().resolve(config.getFileInputTrainingDataCsv());
		}
		
		if (Files.isDirectory(dataPath) || !Files.isReadable(dataPath)){
			throw new StructureLearningException("Can't read data file: " + dataPath);
		}
		
		modelStageLabel = jParameters.optString("modelStageLabel", modelStageLabel);
		missingValue = jParameters.optString("missingValue", missingValue);
		valueSeparator = jParameters.optString("valueSeparator", valueSeparator);
		maxIterations = jParameters.optInt("maxIterations", maxIterations);
		convergenceThreshold = jParameters.optDouble("convergenceThreshold", convergenceThreshold);
		
		JSONObject jKnowledge = jConfig.optJSONObject("knowledge");
		if (jKnowledge != null){
			dataWeight = jParameters.optDouble("dataWeight", dataWeight);
			JSONArray jSkipNodes = jKnowledge.optJSONArray("skipNodes");
			if (jSkipNodes != null){
				for (int i = 0; i < jSkipNodes.length(); i++) {
					Object entry = jSkipNodes.opt(i);
					if (!(entry instanceof String) || ((String) entry).isEmpty()) {
						throw new StructureLearningException(
								"knowledge.skipNodes[" + i + "] must be a non-empty variable name string, got: " + entry);
					}
					nodeDataWeightsCustom.put((String) entry, 0d);
				}
			}

			JSONArray jNodeDataWeightsCustom = jKnowledge.optJSONArray("nodeDataWeightsCustom");
			if (jNodeDataWeightsCustom != null){
				for (int i = 0; i < jNodeDataWeightsCustom.length(); i++) {
					JSONArray nodeWeight = jNodeDataWeightsCustom.optJSONArray(i);
					if (nodeWeight == null || nodeWeight.length() != 2
							|| !(nodeWeight.opt(0) instanceof String) || !(nodeWeight.opt(1) instanceof Number)) {
						throw new StructureLearningException(
								"knowledge.nodeDataWeightsCustom[" + i + "] must be a [variableName, weight] pair, got: "
										+ jNodeDataWeightsCustom.opt(i));
					}
					nodeDataWeightsCustom.put(nodeWeight.getString(0), nodeWeight.getDouble(1));
				}
			}
		}
			
		return this;
	}

	@Override
	public EMParameterLearningExecutor apply() {
		if (dataPath == null || modelStageLabel == null || modelStageLabel.isEmpty() || modelPrefix == null || modelPrefix.isEmpty() || modelPath == null || model == null){
			throw new StructureLearningException("TableLearnerConfigurer is not fully configured before applying");
		}
		EMParameterLearningExecutor executor = new EMParameterLearningExecutor(config);
		executor.setOriginalConfigurer(this);
		return executor;
	}

	public String getModelPrefix() {
		return modelPrefix;
	}

	public void setModelPrefix(String modelPrefix) {
		this.modelPrefix = modelPrefix;
	}

	public Model getModel() {
		return model;
	}

	public void setModel(Model model) {
		this.model = model;
	}

	public Path getDataPath() {
		return dataPath;
	}

	public Path getModelPath() {
		return modelPath;
	}

	public void setModelPath(Path modelPath) {
		this.modelPath = modelPath;
	}

	public String getModelStageLabel() {
		return modelStageLabel;
	}

	public void setModelStageLabel(String modelStageLabel) {
		this.modelStageLabel = modelStageLabel;
	}

	public String getMissingValue() {
		return missingValue;
	}

	public String getValueSeparator() {
		return valueSeparator;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public double getConvergenceThreshold() {
		return convergenceThreshold;
	}

	public double getDataWeight() {
		return dataWeight;
	}

	public Map<String, Double> getNodeDataWeightsCustom() {
		return (Map)Collections.unmodifiableMap(nodeDataWeightsCustom);
	}

	public void resetNodeDataWeightsCustom(){
		nodeDataWeightsCustom.clear();
	}

	public String getNodeLabel() {
		return nodeLabel;
	}

	public void setNodeLabel(String nodeLabel) {
		this.nodeLabel = nodeLabel;
	}

	public boolean isProgressEnabled() {
		return progressEnabled;
	}

	public void setProgressEnabled(boolean progressEnabled) {
		this.progressEnabled = progressEnabled;
	}
}
