package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.result.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class PerformanceEvaluationConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<PerformanceEvaluationConfigurer> {
	
	private Path dataPath;
	private String target = "";
	private final List<String> targets = new ArrayList<>();
	private final List<String> observedNodes = new ArrayList<>();
	private int maxRows = 0; // 0 = use all rows; otherwise subsample to this many
	private boolean calculateRoc = false;
	private String valueSeparator = ",";
	
	private Path outputDirPath;
	private Map<String, String> modelPrefixes;
	private Result pipelineResult;
	private String stageLabel = "";
	private boolean progressEnabled = false;
	
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
		targets.clear();
		JSONArray jTargets = jParameters.optJSONArray("targets");
		if (jTargets != null) {
			for (int i = 0; i < jTargets.length(); i++) {
				String t = jTargets.optString(i, "").trim();
				if (!t.isEmpty() && !targets.contains(t)) {
					targets.add(t);
				}
			}
		}
		// Variables to enter as evidence, explicitly. When empty, each model's own
		// root (parentless) ancestors of the target are used instead.
		//
		// The explicit list matters most when several models are being COMPARED:
		// root ancestors are a property of each model's structure, so a candidate
		// that leaves a variable parentless is handed that variable as evidence
		// while a candidate that models it must predict it — the two are then not
		// scored on the same information. A fixed list also lets the evaluation
		// match what is actually observed when the model is used, which is rarely
		// the exogenous roots.
		observedNodes.clear();
		JSONArray jObserved = jParameters.optJSONArray("observedNodes");
		if (jObserved != null) {
			for (int i = 0; i < jObserved.length(); i++) {
				String o = jObserved.optString(i, "").trim();
				if (!o.isEmpty() && !observedNodes.contains(o)) {
					observedNodes.add(o);
				}
			}
		}
		maxRows = Math.max(0, jParameters.optInt("maxRows", 0));
		calculateRoc = jParameters.optBoolean("calculateRoc", false);
		return this;
	}

	public List<String> getObservedNodes() {
		return observedNodes;
	}

	@Override
	public PerformanceEvaluationExecutor apply() {
		if (dataPath == null){
			throw new StructureLearningException("PerformanceEvaluationConfigurer is not fully configured before applying");
		}
		
		if (getTargets().isEmpty()){
			throw new StructureLearningException("No target node specified for performance evaluation");
		}

		PerformanceEvaluationExecutor executor = new PerformanceEvaluationExecutor(config);
		executor.setOriginalConfigurer(this);
		return executor;
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

	/**
	 * Resolved list of target nodes: the explicit {@code targets} list if
	 * provided, otherwise the single {@code target} (back-compat), otherwise empty.
	 */
	public List<String> getTargets() {
		if (!targets.isEmpty()) {
			return targets;
		}
		List<String> single = new ArrayList<>();
		if (target != null && !target.trim().isEmpty()) {
			single.add(target.trim());
		}
		return single;
	}

	public int getMaxRows() {
		return maxRows;
	}

	public boolean isCalculateRoc() {
		return calculateRoc;
	}

	public void setProgressEnabled(boolean progressEnabled) {
		this.progressEnabled = progressEnabled;
	}

	public boolean isProgressEnabled() {
		return progressEnabled;
	}
}
