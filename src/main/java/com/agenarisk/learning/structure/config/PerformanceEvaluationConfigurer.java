package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.result.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class PerformanceEvaluationConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<PerformanceEvaluationConfigurer> {
	
	private Path dataPath;
	private String target = "";
	private boolean calculateRoc = false;
	private String valueSeparator = ",";
	
	private Path outputDirPath;
	private Map<String, String> modelPrefixes;
	private Result pipelineResult;
	private String stageLabel = "";
	
	public PerformanceEvaluationConfigurer(Config config) {
		super(config);
	}
	
	public PerformanceEvaluationConfigurer() {
		super();
	}
	
	@Override
	public PerformanceEvaluationConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());
		if (jParameters.has("dataPath")){
			dataPath = Paths.get(jParameters.getString("dataPath"));
		}
		else {
			dataPath = config.getPathInput().resolve(config.getFileInputTrainingDataCsv());
		}
		
		if (Files.isDirectory(dataPath) || !Files.isReadable(dataPath)){
			throw new StructureLearningException("Can't read data file: " + dataPath);
		}
		
		valueSeparator = jParameters.optString("valueSeparator", valueSeparator);
		target = jParameters.optString("target", "").trim();
		calculateRoc = jParameters.optBoolean("calculateRoc", false);
		return this;
	}

	@Override
	public PerformanceEvaluationExecutor apply() {
		if (dataPath == null){
			throw new StructureLearningException("PerformanceEvaluationConfigurer is not fully configured before applying");
		}
		
		if (target == null || target.trim().isEmpty()){
			throw new StructureLearningException("Target node not specified");
		}
		
		return new PerformanceEvaluationExecutor(config);
	}

	public Path getDataPath() {
		return dataPath;
	}

	public String getValueSeparator() {
		return valueSeparator;
	}

	public void setOutputDirPath(Path outputDirPath) {
		this.outputDirPath = outputDirPath;
	}

	public void setModelPrefixes(Map<String, String> modelPrefixes) {
		this.modelPrefixes = modelPrefixes;
	}

	public Map<String, String> getModelPrefixes() {
		return modelPrefixes;
	}

	public Path getOutputDirPath() {
		return outputDirPath;
	}
	
	public void setPipelineResult(Result pipelineResult) {
		this.pipelineResult = pipelineResult;
	}

	public Result getPipelineResult() {
		return pipelineResult;
	}

	public String getStageLabel() {
		return stageLabel;
	}

	public void setStageLabel(String evaluationLabel) {
		this.stageLabel = evaluationLabel;
	}

	public void setDataPath(Path dataPath) {
		this.dataPath = dataPath;
	}

	public String getTarget() {
		return target;
	}

	public boolean isCalculateRoc() {
		return calculateRoc;
	}
}
