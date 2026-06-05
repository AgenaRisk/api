package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class HcStableConfigurer extends PrunableLearningConfigurer<HcStableConfigurer> implements ConfigurableFromJson<HcStableConfigurer> {
	
	public HcStableConfigurer(Config config) {
		super(config);
	}
	
	public HcStableConfigurer() {
		super();
	}
	
	/**
	 * Finalize configuration and proceed to execution preparation
	 * @return 
	 */
	@Override
	public StructureLearnerExecutor apply() {
		config.setLearningAlgorithm(Config.LearningAlgorithm.HCStable);
		return new StructureLearnerExecutor(config);
	}
	
	/**
	 * Allows to add custom configuration of the knowledge component
	 * 
	 * @return 
	 */
	@Override
	public KnowledgeConfigurer<HcStableConfigurer> getKnowledgeConfiguration() {
		return (KnowledgeConfigurer<HcStableConfigurer>) super.getKnowledgeConfiguration(this);
	}

	@Override
	public HcStableConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());
		configureBicLogFromJson(jParameters);
		configurePruningFromJson(jParameters);
		try {
			getKnowledgeConfiguration().configureFromJson(jConfig);
		}
		catch(Exception ex){
			throw new StructureLearningException("Failed to read knowledge configuration from JSON", ex);
		}
		return this;
	}
	
}
