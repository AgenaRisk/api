package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class SaiyanHConfigurer extends LearningConfigurer implements ConfigurableFromJson<SaiyanHConfigurer> {

	/**
	 * Score and distance derived from single type
	 */
	public enum DiscrepancyType {
		Mutual_Information,
		Mean_Absolute,
		Max_Absolute,
		MeanMax_Absolute,
		Mean_Relative,
		Max_Relative
	}
	
	public SaiyanHConfigurer(Config config) {
		super(config);
	}
	
	public SaiyanHConfigurer() {
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
	 * Sets Maximum Mean Discrepancy (MMD) type and Score and Distance types respectively
	 * @param dType
	 * @return 
	 */
	public SaiyanHConfigurer setDiscrepancyType(DiscrepancyType dType){
		switch (dType) {
			case Mutual_Information:
				config.setLearningSaiyanHdiscScoreType("MI");
				config.setLearningSaiyanHdiscDistanceType("NA");
				break;
			case Mean_Absolute:
				config.setLearningSaiyanHdiscScoreType("Mean");
				config.setLearningSaiyanHdiscDistanceType("Absolute");
				break;
			case Max_Absolute:
				config.setLearningSaiyanHdiscScoreType("Max");
				config.setLearningSaiyanHdiscDistanceType("Absolute");
				break;
			case MeanMax_Absolute:
				config.setLearningSaiyanHdiscScoreType("MeanMax");
				config.setLearningSaiyanHdiscDistanceType("Absolute");
				break;
			case Mean_Relative:
				config.setLearningSaiyanHdiscScoreType("Mean");
				config.setLearningSaiyanHdiscDistanceType("Relative");
				break;
			case Max_Relative:
				config.setLearningSaiyanHdiscScoreType("Max");
				config.setLearningSaiyanHdiscDistanceType("Absolute");
				break;
			default:
				throw new StructureLearningException("Invalid discrepancy type");
		}
		return this;
	}
	
	/**
	 * If set, will save association scores to config.getPathOutput()/SaiyanH/marginalDep.csv
	 * @param save
	 * @return 
	 */
	public SaiyanHConfigurer setSaveAssociationalScores(boolean save){
		config.setLearningSaiyanHSaveAssocScores(save);
		return this;
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
	public SaiyanHConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());
		configureBicLogFromJson(jParameters);
		if (jParameters.has("maximumMeanDiscrepancyType")){
			try {
				DiscrepancyType mmdType = DiscrepancyType.valueOf(jParameters.getString("maximumMeanDiscrepancyType"));
				setDiscrepancyType(mmdType);
			}
			catch(Exception ex){
				throw new StructureLearningException("Invalid maximumMeanDiscrepancyType", ex);
			}
		}
		try {
			getKnowledgeConfiguration().configureFromJson(jConfig);
		}
		catch(Exception ex){
			throw new StructureLearningException("Failed to read knowledge configuration from JSON", ex);
		}
		
		return this;
	}

}
