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
public class TableLearningConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<TableLearningConfigurer> {
	
	private Path dataPath;
	private Path modelPath;
	private String modelStageLabel;
	private String modelPrefix;
	private Model model;
	private String missingValue = "NA";
	private String valueSeparator = ",";
	private int maxIterations = 50;
	private double convergenceThreshold = 0.01;
	
	private double dataWeight = 1;
	private final HashMap<String, Double> nodeDataWeightsCustom = new HashMap<>();
	
	public TableLearningConfigurer(Config config) {
		super(config);
	}
	
	public TableLearningConfigurer() {
		super();
	}
	
	@Override
	public TableLearningConfigurer configureFromJson(JSONObject jConfig) {
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
					String nodeId = jSkipNodes.getString(i);
					nodeDataWeightsCustom.put(nodeId, 0d);
				}
			}
		
			JSONArray jNodeDataWeightsCustom = jKnowledge.optJSONArray("nodeDataWeightsCustom");
			if (jNodeDataWeightsCustom != null){
				for (int i = 0; i < jNodeDataWeightsCustom.length(); i++) {
					JSONArray nodeWeight = jNodeDataWeightsCustom.getJSONArray(i);
					String nodeId = nodeWeight.getString(0);
					double weight = nodeWeight.getDouble(1);
					nodeDataWeightsCustom.put(nodeId, weight);
				}
			}
		}
			
		return this;
	}

	@Override
	public TableLearningExecutor apply() {
		if (dataPath == null || modelStageLabel == null || modelStageLabel.isEmpty() || modelPrefix == null || modelPrefix.isEmpty() || modelPath == null || model == null){
			throw new StructureLearningException("TableLearnerConfigurer is not fully configured before applying");
		}
		return new TableLearningExecutor(config);
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
	
	
}
