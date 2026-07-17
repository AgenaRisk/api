package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionKnowledge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.json.JSONObject;

/**
 * Configures a Regression Structure Discovery run: one new, self-contained greedy-search algorithm scored with a
 * decomposable regression-based BIC over mixed continuous/discrete data, running alongside (never replacing) the
 * legacy discrete-BIC engine ({@code learnWithHc()}/{@code learnWithTabu()}/etc). See
 * {@link com.agenarisk.learning.structure.regressiondiscovery.RegressionStructureSearch} for the search itself.
 * <br>
 * Unlike the legacy engine's configurers, this never touches {@code Config}'s algorithm/constraint fields or writes
 * CSV constraint files - knowledge is supplied directly as a {@link RegressionKnowledge} object, evaluated in-process
 * by the search.
 *
 * @author Eugene Dementiev
 */
public class RegressionStructureConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<RegressionStructureConfigurer> {

	private Path dataPath;
	private Path modelPath;
	private String modelStageLabel;
	private String modelPrefix;
	private Model model;
	private String missingValue = "";
	private String valueSeparator = ",";
	private double ridgeLambda = com.agenarisk.learning.structure.regression.MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA;
	private int maxParentsPerNode = 5;
	private int maxIterations = 500;
	private RegressionKnowledge knowledge = new RegressionKnowledge();

	public RegressionStructureConfigurer(Config config) {
		super(config);
	}

	public RegressionStructureConfigurer() {
		super();
	}

	@Override
	public RegressionStructureConfigurer configureFromJson(JSONObject jConfig) {
		JSONObject jParameters = Optional.ofNullable(jConfig.optJSONObject("parameters")).orElse(new JSONObject());

		if (jParameters.has("dataPath")){
			dataPath = Paths.get(jParameters.getString("dataPath"));
			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());
			config.setPathInput(dataPath.toString());
		}
		else {
			dataPath = config.getPathInput().resolve(config.getFileInputTrainingDataCsv());
		}

		if (Files.isDirectory(dataPath) || !Files.isReadable(dataPath)){
			throw new StructureLearningException("Can't read data file: " + dataPath);
		}

		modelStageLabel = jParameters.optString("modelStageLabel", modelStageLabel);
		missingValue = jParameters.optString("missingValue", missingValue);
		valueSeparator = jParameters.optString("valueSeparator", valueSeparator);
		ridgeLambda = jParameters.optDouble("ridgeLambda", ridgeLambda);
		maxParentsPerNode = jParameters.optInt("maxParentsPerNode", maxParentsPerNode);
		maxIterations = jParameters.optInt("maxIterations", maxIterations);

		if (jConfig.has("knowledge")){
			knowledge = RegressionKnowledge.fromJson(jConfig.getJSONObject("knowledge"));
		}

		return this;
	}

	@Override
	public RegressionStructureSearchExecutor apply() {
		if (dataPath == null || modelStageLabel == null || modelStageLabel.isEmpty() || modelPrefix == null || modelPrefix.isEmpty() || modelPath == null || model == null){
			throw new StructureLearningException("RegressionStructureConfigurer is not fully configured before applying");
		}
		RegressionStructureSearchExecutor executor = new RegressionStructureSearchExecutor(config);
		executor.setOriginalConfigurer(this);
		return executor;
	}

	public String getModelPrefix() {
		return modelPrefix;
	}

	public void setModelPrefix(String modelPrefix) {
		this.modelPrefix = modelPrefix;
	}

	public Model getModel() {
		return model;
	}

	public void setModel(Model model) {
		this.model = model;
	}

	public Path getDataPath() {
		return dataPath;
	}

	public Path getModelPath() {
		return modelPath;
	}

	public void setModelPath(Path modelPath) {
		this.modelPath = modelPath;
	}

	public String getModelStageLabel() {
		return modelStageLabel;
	}

	public void setModelStageLabel(String modelStageLabel) {
		this.modelStageLabel = modelStageLabel;
	}

	public String getMissingValue() {
		return missingValue;
	}

	public String getValueSeparator() {
		return valueSeparator;
	}

	public double getRidgeLambda() {
		return ridgeLambda;
	}

	public int getMaxParentsPerNode() {
		return maxParentsPerNode;
	}

	public void setMaxParentsPerNode(int maxParentsPerNode) {
		this.maxParentsPerNode = maxParentsPerNode;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public void setMaxIterations(int maxIterations) {
		this.maxIterations = maxIterations;
	}

	public RegressionKnowledge getKnowledge() {
		return knowledge;
	}

	public RegressionStructureConfigurer setKnowledge(RegressionKnowledge knowledge) {
		this.knowledge = knowledge;
		return this;
	}
}
