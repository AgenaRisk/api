package com.agenarisk.learning.structure.config;

import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class TabuConfigurer extends PrunableLearningConfigurer<TabuConfigurer> implements Configurable, ConfigurableFromJson<TabuConfigurer> {
	
	public TabuConfigurer(Config config) {
		super(config);
	}
	
	public TabuConfigurer() {
		super();
	}
	
	/**
	 * Finalize configuration and proceed to execution preparation
	 * @return 
	 */
	@Override
	public StructureLearnerExecutor apply() {
		config.setLearningAlgorithm(Config.LearningAlgorithm.TABU);
		return new StructureLearnerExecutor(config);
	}
	
	/**
	 * Allows to add custom configuration of the knowledge component
	 * 
	 * @return 
	 */
	@Override
	public KnowledgeConfigurer<TabuConfigurer> getKnowledgeConfiguration() {
		return (KnowledgeConfigurer<TabuConfigurer>) super.getKnowledgeConfiguration(this);
	}
	
	@Override
	public TabuConfigurer configureFromJson(JSONObject jConfig) {
		configureBicLogFromJson(jConfig);
		configurePruningFromJson(jConfig);
		return this;
	}
}
