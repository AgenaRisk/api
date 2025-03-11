package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Actual implementation type of Configurer
 */
public abstract class BicLogConfigurer<T extends Configurer> extends ApplicableConfigurer<T>{
	public BicLogConfigurer(Config config) {
		super(config);
	}
	
	public BicLogConfigurer() {
		super();
	}
	
	/**
	 * Allowed values: 2, 10, e
	 * @param log
	 * @return this configurer
	 */
	public T setBicLog(String log){
		switch (log) {
			case "2":
			case "10":
			case "e":
				config.setEvalBicLog(log);
				break;
			default:
				throw new StructureLearningException("Invalid BIC log being set, only values 2, 10 and e allowed");
		}
		return (T)this;
	}
	
	protected T configureBicLogFromJson(JSONObject jConfig){
		if (jConfig.has("bicLog")){
			this.setBicLog(jConfig.getString("bicLog"));
		}
		return (T)this;
	}
	
}
