package com.agenarisk.learning.structure.config;

/**
 *
 * @author Eugene Dementiev
 * @param <T>
 */
public interface Configurable<T extends Configurer> {
	public Executable apply();
}
