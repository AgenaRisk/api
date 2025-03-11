package com.agenarisk.learning.structure.config;

import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class AveragingConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<AveragingConfigurer> {
	
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
		return this;
	}

}
