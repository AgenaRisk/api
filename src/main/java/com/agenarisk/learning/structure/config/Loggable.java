package com.agenarisk.learning.structure.config;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Implementation type of this Configurer
 */
public interface Loggable<T extends Configurer> {
	public T setLoggingEnabled(boolean enabled);
}
