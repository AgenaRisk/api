package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class MahcConfigurer extends PrunableLearningConfigurer<MahcConfigurer> implements ConfigurableFromJson<MahcConfigurer> {
	
	public MahcConfigurer(Config config) {
		super(config);
	}
	
	public MahcConfigurer() {
		super();
	}
	
	/**
	 * Finalize configuration and proceed to execution preparation
	 * @return 
	 */
	@Override
	public StructureLearnerExecutor apply() {
		config.setLearningAlgorithm(Config.LearningAlgorithm.HC);
		return new StructureLearnerExecutor(config);
	}
	
	/**
	 * Allowed values: 2, 10, e
	 * @param log
	 * @return this configurer
	 */
	@Override
	public MahcConfigurer setBicLog(String log) {
		return (MahcConfigurer)super.setBicLog(log);
	}
	
	/**
	 * Allows to add custom configuration of the knowledge component
	 * 
	 * @return 
	 */
	@Override
	public KnowledgeConfigurer<MahcConfigurer> getKnowledgeConfiguration() {
		return (KnowledgeConfigurer<MahcConfigurer>) super.getKnowledgeConfiguration(this);
	}
	
	public int getMaxInDegreePreProcessing(){
		return config.getLearningMahcMaxInDegreePreProc();
	}
	
	public MahcConfigurer setMaxInDegreePreProcessing(int maxInDegreePreProc){
		if (maxInDegreePreProc != 2 || maxInDegreePreProc != 3){
			throw new StructureLearningException("MaxInDegreePreProcessing for MAHC can only be 2 or 3");
		}
		config.setLearningMahcMaxInDegreePreProc(maxInDegreePreProc);
		return this;
	}
	
	@Override
	public MahcConfigurer configureFromJson(JSONObject jConfig) {
		configureBicLogFromJson(jConfig);
		configurePruningFromJson(jConfig);
		if (jConfig.has("maxInDegreePreProcessing")){
			setMaxInDegreePreProcessing(jConfig.getInt("maxInDegreePreProcessing"));
		}
		return this;
	}
	
}
