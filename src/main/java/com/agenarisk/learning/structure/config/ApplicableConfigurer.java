package com.agenarisk.learning.structure.config;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Actual implementation type of Configurer
 */
public abstract class ApplicableConfigurer <T extends Configurer<T>> extends Configurer<T> implements Configurable<T> {
	public ApplicableConfigurer(Config config) {
		super(config);
	}
	
	public ApplicableConfigurer() {
		super();
	}
}
