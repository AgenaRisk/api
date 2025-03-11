package com.agenarisk.learning.structure.config;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Actual implementation type of Configurer
 */
public abstract class ApplicableConfigurer <T extends Configurer> extends Configurer implements Configurable {
	public ApplicableConfigurer(Config config) {
		super(config);
	}
	
	public ApplicableConfigurer() {
		super();
	}
}
