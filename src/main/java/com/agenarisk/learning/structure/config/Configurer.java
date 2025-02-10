package com.agenarisk.learning.structure.config;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Implementation type of this Configurer
 */
public abstract class Configurer<T extends Configurer> implements Loggable {
	protected final Config config;

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
}
