package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.logger.BLogger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class GenerationConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<GenerationConfigurer> {
	
	public static enum Strategy {
		random
	}
	
	private int maximumEdgeCount = 0;
	private boolean statesFromData = false;
	private Path dataPath;
	private Path modelPath;
	private Strategy strategy = Strategy.random;
	private Model model;
	private HashMap<String, LinkedHashMap<String, Integer>> frequencyTables;
	private HashMap<String, Integer> variableFrequency = new HashMap<>();
	
	public GenerationConfigurer(Config config) {
		super(config);
	}
	
	public GenerationConfigurer() {
		super();
	}

	@Override
	public GenerationExecutor apply() {
		return new GenerationExecutor(config);
	}

	@Override
	public GenerationConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());
		maximumEdgeCount = jParameters.optInt("maximumEdgeCount", 0);
		statesFromData = jParameters.optBoolean("statesFromData", false);
		try {
			strategy = Strategy.valueOf(jParameters.optString("strategy", Strategy.random.name()));
		}
		catch (Exception ex){
			BLogger.logConditional("Invalid model generation strategy " + jParameters.optString("strategy") + ", falling back to default: " + strategy.name());
		}
		if (jParameters.has("dataPath")){
			dataPath = Paths.get(jParameters.getString("dataPath"));
		}
		else {
			dataPath = config.getPathInput().resolve(config.getFileInputTrainingDataCsv());
		}
		return this;
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
	
	public int getMaximumEdgeCount() {
		return maximumEdgeCount;
	}

	public Strategy getStrategy() {
		return strategy;
	}

	public Model getModel() {
		return model;
	}

	public void setModel(Model model) {
		this.model = model;
	}

	public boolean isStatesFromData() {
		return statesFromData;
	}
}
