package com.agenarisk.learning.structure.config;

import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class GesConfigurer extends LearningConfigurer<GesConfigurer> implements ConfigurableFromJson<GesConfigurer> {

	public GesConfigurer(Config config) {
		super(config);
	}
	
	public GesConfigurer() {
		super();
	}
	
	/**
	 * Finalize configuration and proceed to execution preparation
	 * @return 
	 */
	@Override
	public Executable apply() {
		config.setLearningAlgorithm(Config.LearningAlgorithm.SaiyanH);
		return new StructureLearnerExecutor(config);
	}
	
	/**
	 * Allows to add custom configuration of the knowledge component
	 * 
	 * @return 
	 */
	@Override
	public KnowledgeConfigurer getKnowledgeConfiguration() {
		return super.getKnowledgeConfiguration(this);
	}
		
	@Override
	public GesConfigurer configureFromJson(JSONObject jConfig) {
		configureBicLogFromJson(jConfig);
		return this;
	}
}
