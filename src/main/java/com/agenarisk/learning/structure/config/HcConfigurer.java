package com.agenarisk.learning.structure.config;

import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class HcConfigurer extends PrunableLearningConfigurer<HcConfigurer> implements ConfigurableFromJson<HcConfigurer> {
	
	public HcConfigurer(Config config) {
		super(config);
	}
	
	public HcConfigurer() {
		super();
	}
	
	/**
	 * Finalize configuration and proceed to execution preparation
	 * @return 
	 */
	@Override
	public StructureLearnerExecutor apply() {
		config.setLearningAlgorithm(Config.LearningAlgorithm.HC);
		return new StructureLearnerExecutor(config);
	}
	
	/**
	 * Allows to add custom configuration of the knowledge component
	 * 
	 * @return 
	 */
	@Override
	public KnowledgeConfigurer<HcConfigurer> getKnowledgeConfiguration() {
		return (KnowledgeConfigurer<HcConfigurer>) super.getKnowledgeConfiguration(this);
	}

	@Override
	public HcConfigurer configureFromJson(JSONObject jConfig) {
		configureBicLogFromJson(jConfig);
		configurePruningFromJson(jConfig);
		return this;
	}
	
}
