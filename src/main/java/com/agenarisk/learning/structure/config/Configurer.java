package com.agenarisk.learning.structure.config;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Implementation type of this Configurer
 */
public abstract class Configurer<T extends Configurer<T>> implements Loggable {
	protected final Config config;
	protected final HashMap<String, String> data = new HashMap<>();

	public Configurer(Config config) {
		this.config = config;
	}
	
	public Configurer() {
		this.config = Config.getInstance();
	}

	@Override
	public T setLoggingEnabled(boolean enabled) {
		config.setLoggingEnabled(enabled);
		return (T)this;
	}
	
	public Config getConfig(){
		return config;
	}
	
	public HashMap<String, String> getData() {
		return data;
	}
	
	public T useData(Map<String, String> moreData){
		data.putAll(moreData);
		return (T)this;
	}
}
