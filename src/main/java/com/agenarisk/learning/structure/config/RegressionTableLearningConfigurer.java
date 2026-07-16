package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.json.JSONObject;

/**
 * Configures an OLS-based table learning run: fits continuous nodes' tables directly from data via linear
 * regression (partitioned per combination of their categorical parents' states), as an alternative to the
 * EM/frequency-based learning in {@link TableLearningConfigurer}.
 * <br>
 * Unlike EM-based table learning, this does not require any prior binning or node type remapping - continuous nodes
 * are regressed on directly, and rows are selected independently per node (and per partition) based on whatever
 * columns that particular fit actually needs, so partial data elsewhere in the dataset doesn't exclude a row.
 * <br>
 * Categorical (Boolean/Labelled/DiscreteReal) targets are not yet learned by this configurer - see
 * {@code RegressionTableLearningExecutor} for how they're currently reported.
 *
 * @author Eugene Dementiev
 */
public class RegressionTableLearningConfigurer extends ApplicableConfigurer implements Configurable, ConfigurableFromJson<RegressionTableLearningConfigurer> {

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

	public RegressionTableLearningConfigurer(Config config) {
		super(config);
	}

	public RegressionTableLearningConfigurer() {
		super();
	}

	@Override
	public RegressionTableLearningConfigurer configureFromJson(JSONObject jConfig) {
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

		return this;
	}

	@Override
	public RegressionTableLearningExecutor apply() {
		if (dataPath == null || modelStageLabel == null || modelStageLabel.isEmpty() || modelPrefix == null || modelPrefix.isEmpty() || modelPath == null || model == null){
			throw new StructureLearningException("RegressionTableLearningConfigurer is not fully configured before applying");
		}
		RegressionTableLearningExecutor executor = new RegressionTableLearningExecutor(config);
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
}
