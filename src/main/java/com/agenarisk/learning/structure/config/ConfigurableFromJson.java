package com.agenarisk.learning.structure.config;

import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 * @param <T>
 */
public interface ConfigurableFromJson<T extends Configurer> {
	public T configureFromJson(JSONObject jConfig);
}
