package com.agenarisk.learning.structure.config;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Actual implementation type of LearningConfigurer
 */
public abstract class LearningConfigurer<T extends LearningConfigurer<T>> extends BicLogConfigurer<T> {
	
	public LearningConfigurer(Config config) {
		super(config);
	}
	
	public LearningConfigurer() {
		super();
	}
	
	protected KnowledgeConfigurer<T> getKnowledgeConfiguration(T parent) {
		KnowledgeConfigurer<T> knowledgeConfigurer = new KnowledgeConfigurer<>(config, parent);
		knowledgeConfigurer.useData(parent.getData());
		return knowledgeConfigurer;
	}
	
	public abstract KnowledgeConfigurer<T> getKnowledgeConfiguration();

}
