package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.json.JSONObject;

/**
 * Configures a regression-based parameter learning run: fits every node's table against its already-fixed parents
 * (continuous targets via OLS, categorical targets with only categorical parents via ridge-regularized multinomial
 * logistic regression baked to a manual NPT, categorical targets with any continuous parent via a persisted
 * {@code MultinomialLogit(...)} expression) - the canonical, sole regression-based parameter learner, alongside
 * {@link EMParameterLearningConfigurer} (EM-based) and {@link RegressionStructureConfigurer} (structure + parameters
 * together).
 *
 * @author Eugene Dementiev
 */
public class RegressionParameterLearningConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<RegressionParameterLearningConfigurer> {

	public static final String RESIDUAL_MODE_NORMAL = "Normal";
	public static final String RESIDUAL_MODE_ARITHMETIC = "Arithmetic";

	private Path dataPath;
	private Path modelPath;
	private String modelStageLabel;
	private String modelPrefix;
	private Model model;
	private String missingValue = "";
	private String valueSeparator = ",";
	private String residualMode = RESIDUAL_MODE_NORMAL;
	private int minRowsPerPartition = 5;
	private double ridgeLambda = com.agenarisk.learning.structure.regression.MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA;
	private String nodeLabel = "";
	private boolean progressEnabled = false;
	private final java.util.Map<String, com.agenarisk.learning.structure.regression.DeterministicExpressions.Declaration> deterministicExpressions = new java.util.LinkedHashMap<>();

	public RegressionParameterLearningConfigurer(Config config) {
		super(config);
	}

	public RegressionParameterLearningConfigurer() {
		super();
	}

	@Override
	public RegressionParameterLearningConfigurer configureFromJson(JSONObject jConfig) {
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
		residualMode = jParameters.optString("residualMode", residualMode);
		minRowsPerPartition = jParameters.optInt("minRowsPerPartition", minRowsPerPartition);
		ridgeLambda = jParameters.optDouble("ridgeLambda", ridgeLambda);

		if (!RESIDUAL_MODE_NORMAL.equalsIgnoreCase(residualMode) && !RESIDUAL_MODE_ARITHMETIC.equalsIgnoreCase(residualMode)){
			throw new StructureLearningException("residualMode must be '" + RESIDUAL_MODE_NORMAL + "' or '" + RESIDUAL_MODE_ARITHMETIC + "', got: " + residualMode);
		}

		// Nodes whose table is an exact arithmetic function of their parents,
		// given as { nodeId: expression }. Written as Arithmetic(expression)
		// instead of being fitted: a regression over a relation that holds
		// exactly returns R2 = 1 and a residual variance that only escapes being
		// zero because it is floored, so the fitted node asserts an uncertainty
		// that does not exist and carries coefficients estimated from a sample
		// where the true ones are known by algebra.
		deterministicExpressions.clear();
		deterministicExpressions.putAll(com.agenarisk.learning.structure.regression.DeterministicExpressions
				.parse(jParameters.optJSONObject("deterministicExpressions")));

		return this;
	}

	public java.util.Map<String, com.agenarisk.learning.structure.regression.DeterministicExpressions.Declaration> getDeterministicExpressions() {
		return deterministicExpressions;
	}

	@Override
	public RegressionParameterLearningExecutor apply() {
		if (dataPath == null || modelStageLabel == null || modelStageLabel.isEmpty() || modelPrefix == null || modelPrefix.isEmpty() || modelPath == null || model == null){
			throw new StructureLearningException("RegressionParameterLearningConfigurer is not fully configured before applying");
		}
		RegressionParameterLearningExecutor executor = new RegressionParameterLearningExecutor(config);
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

	public String getResidualMode() {
		return residualMode;
	}

	public int getMinRowsPerPartition() {
		return minRowsPerPartition;
	}

	public double getRidgeLambda() {
		return ridgeLambda;
	}

	public String getNodeLabel() {
		return nodeLabel;
	}

	public void setNodeLabel(String nodeLabel) {
		this.nodeLabel = nodeLabel;
	}

	public boolean isProgressEnabled() {
		return progressEnabled;
	}

	public void setProgressEnabled(boolean progressEnabled) {
		this.progressEnabled = progressEnabled;
	}
}
