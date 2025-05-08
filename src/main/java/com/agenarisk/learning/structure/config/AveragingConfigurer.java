package com.agenarisk.learning.structure.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class AveragingConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<AveragingConfigurer> {
	
	private boolean statesFromData = false;
	private Path dataPath;
	
	public AveragingConfigurer(Config config) {
		super(config);
	}
	
	public AveragingConfigurer() {
		super();
	}

	@Override
	public AveragingExecutor apply() {
		return new AveragingExecutor(config);
	}

	@Override
	public AveragingConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());
		config.setAveragingMinimumEdgeAppearanceCountToKeep(jParameters.optInt("minimumEdgeAppearanceCountToKeep", 1));
		statesFromData = jParameters.optBoolean("statesFromData", false);
		if (jParameters.has("dataPath")){
			dataPath = Paths.get(jParameters.getString("dataPath"));
		}
		else {
			dataPath = config.getPathInput().resolve(config.getFileInputTrainingDataCsv());
		}
		return this;
	}

	public boolean isStatesFromData() {
		return statesFromData;
	}

	public Path getDataPath() {
		return dataPath;
	}
}
