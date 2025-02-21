package com.agenarisk.learning.structure.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class EvaluationConfigurer extends BicLogConfigurer<EvaluationConfigurer> implements Configurable, ConfigurableFromJson<EvaluationConfigurer> {
	public EvaluationConfigurer(Config config) {
		super(config);
	}
	
	public EvaluationConfigurer() {
		super();
	}
	
	/**
	 * Finalize configuration and proceed to execution preparation
	 * @return 
	 */
	@Override
	public EvaluationExecutor apply() {
		return new EvaluationExecutor(config);
	}
	
	@Override
	public EvaluationConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());
		configureBicLogFromJson(jParameters);
		config.setEvalBic(true);
		config.setLogLikelyhoodScore(jParameters.optBoolean("logLikelyhoodScore", false));
		if (jParameters.has("evaluationDataPath")){
			Path dataPath = Paths.get(jParameters.getString("evaluationDataPath"));
			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());
			config.setPathInput(dataPath.toString());
		}
		return this;
	}
}
