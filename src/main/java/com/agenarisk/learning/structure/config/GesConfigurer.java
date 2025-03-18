package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class GesConfigurer extends LearningConfigurer<GesConfigurer> implements ConfigurableFromJson<GesConfigurer> {

	public GesConfigurer(Config config) {
		super(config);
	}
	
	public GesConfigurer() {
		super();
	}
	
	/**
	 * Finalize configuration and proceed to execution preparation
	 * @return 
	 */
	@Override
	public Executable apply() {
		config.setLearningAlgorithm(Config.LearningAlgorithm.SaiyanH);
		return new StructureLearnerExecutor(config);
	}
	
	/**
	 * Allows to add custom configuration of the knowledge component
	 * 
	 * @return 
	 */
	@Override
	public KnowledgeConfigurer getKnowledgeConfiguration() {
		return super.getKnowledgeConfiguration(this);
	}
		
	@Override
	public GesConfigurer configureFromJson(JSONObject jConfig) {
		configureBicLogFromJson(Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject()));
		try {
			getKnowledgeConfiguration().configureFromJson(jConfig);
		}
		catch(Exception ex){
			throw new StructureLearningException("Failed to read knowledge configuration from JSON", ex);
		}
		return this;
	}
}
