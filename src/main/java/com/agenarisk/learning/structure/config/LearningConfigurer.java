package com.agenarisk.learning.structure.config;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Actual implementation type of LearningConfigurer
 */
public abstract class LearningConfigurer<T extends LearningConfigurer> extends BicLogConfigurer<T> {
	
	public LearningConfigurer(Config config) {
		super(config);
	}
	
	public LearningConfigurer() {
		super();
	}
	
	protected KnowledgeConfigurer<T> getKnowledgeConfiguration(T parent) {
		return new KnowledgeConfigurer<>(config, parent);
	}
	
	public abstract KnowledgeConfigurer<T> getKnowledgeConfiguration();

}
