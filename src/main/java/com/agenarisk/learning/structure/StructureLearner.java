package com.agenarisk.learning.structure;

import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.ConfiguredExecutor;
import com.agenarisk.learning.structure.config.GesConfigurer;
import com.agenarisk.learning.structure.config.HcConfigurer;
import com.agenarisk.learning.structure.config.MahcConfigurer;
import com.agenarisk.learning.structure.config.SaiyanHConfigurer;
import com.agenarisk.learning.structure.config.TabuConfigurer;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class StructureLearner {
	public SaiyanHConfigurer learnWithSaiyanH(){
		Config.reset();
		return new SaiyanHConfigurer(Config.getInstance());
	}
	
	public HcConfigurer learnWithHc(){
		Config.reset();
		return new HcConfigurer(Config.getInstance());
	}
	
	public MahcConfigurer learnWithMahc(){
		Config.reset();
		return new MahcConfigurer(Config.getInstance());
	}
	
	public GesConfigurer learnWithGes(){
		Config.reset();
		return new GesConfigurer(Config.getInstance());
	}
	
	public TabuConfigurer learnWithTabu(){
		Config.reset();
		return new TabuConfigurer(Config.getInstance());
	}
	
	public void executeJson(String jsonString){
		JSONObject json;
		try {
			json = new JSONObject(jsonString);
		}
		catch (Exception ex){
			throw new StructureLearningException("Failed to read config from JSON", ex);
		}
		
		executeJson(json);
	}
	
	public void executeJson(JSONObject json){
		ConfiguredExecutor.executeFromJson(json);
	}
}
