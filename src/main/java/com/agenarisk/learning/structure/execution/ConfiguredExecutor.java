package com.agenarisk.learning.structure.execution;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.api.util.TempDirCleanup;
import com.agenarisk.learning.structure.config.BicLogConfigurer;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.EvaluationConfigurer;
import com.agenarisk.learning.structure.config.GesConfigurer;
import com.agenarisk.learning.structure.config.HcConfigurer;
import com.agenarisk.learning.structure.config.MahcConfigurer;
import com.agenarisk.learning.structure.config.SaiyanHConfigurer;
import com.agenarisk.learning.structure.config.TabuConfigurer;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.result.Discovery;
import com.agenarisk.learning.structure.execution.result.Evaluation;
import com.agenarisk.learning.structure.execution.result.Result;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.utility.CmpxStructureExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.apache.commons.lang3.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class ConfiguredExecutor {
	private static final Pattern INVALID_LABEL_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]|(\\.\\.)|[/]");
	
	private ArrayList<ArrayList<String>> data = null;
	private Path dataFilePath = null;
	private Path outputDirPath = null;
	private Path inputDirPath = null;
	
	private Result result = new Result();
	
	private Config config;

	public ConfiguredExecutor(Config config) {
		this.config = config;
	}
	
	public ConfiguredExecutor() {
		this.config = Config.getInstance();
	}

	public ArrayList<ArrayList<String>> getData() {
		return data;
	}

	public void setData(ArrayList<ArrayList<String>> data) {
		this.data = data;
	}

	public Path getDataFilePath() {
		return dataFilePath;
	}

	public void setDataFilePath(Path dataFilePath) {
		this.dataFilePath = dataFilePath;
	}

	public Path getInputDirPath() {
		return inputDirPath;
	}

	public void setInputDirPath(Path inputDirPath) {
		this.inputDirPath = inputDirPath;
	}
	
	public Path getOutputDirPath() {
		return outputDirPath;
	}

	public void setOutputDirPath(Path outputDirPath) {
		this.outputDirPath = outputDirPath;
	}

	public Config getConfig() {
		return config;
	}

	private void setConfig(Config config) {
		this.config = config;
	}

	public Result getResult() {
		return result;
	}
	
	private void createTempDirs(JSONObject jConfig){
		try {
			setOutputDirPath(Paths.get(jConfig.optString("outputDirPath")));
		}
		catch (Exception ex){
			try {
				setOutputDirPath(Files.createTempDirectory("agenarisk"));
				TempDirCleanup.registerTempDirectory(getOutputDirPath());
			}
			catch (IOException ioex){
				throw new StructureLearningException("Failed to create output directory", ioex);
			}
		}

		try {
			setInputDirPath(Files.createTempDirectory("agenarisk"));
			TempDirCleanup.registerTempDirectory(getInputDirPath());
		}
		catch (IOException ioex){
			throw new StructureLearningException("Failed to create output directory", ioex);
		}
	}
	
	public static ConfiguredExecutor executeFromJson(JSONObject jConfig){
		HashMap<String, String> discoveryPrefixes = new HashMap<>();
		ConfiguredExecutor executor = new ConfiguredExecutor(Config.getInstance());
		
		try {
			executor.setDataFilePath(Paths.get(jConfig.optString("dataFilePath")));
		}
		catch (Exception ex){
			throw new StructureLearningException("Failed to read dataFilePath from JSON", ex);
		}
		
		if (jConfig.has("data")){
			throw new NotImplementedException("data in JSON not yet supported");
		}
		
		JSONArray jExecutions = jConfig.optJSONArray("executions");
		if (jExecutions == null){
			// Nothing to do
			return executor;
		}
				
		for(int iExecution = 0; iExecution < jExecutions.length(); iExecution++){
			JSONObject jExecution = jExecutions.optJSONObject(iExecution);
			if (jExecution == null){
				continue;
			}
			
			executor.setConfig(Config.reset());
			BicLogConfigurer configurableExecution;

			String executionType = jExecution.optString("execution");
			String executionLabel = jExecution.optString("label");
			
			executor.getConfig().setFileInputTrainingDataCsv(executor.getDataFilePath().getFileName().toString());
			executor.setInputDirPath(executor.getDataFilePath().getParent());
			executor.getConfig().setPathInput(executor.getInputDirPath().toString());
			
			switch (executionType) {
				case "discovery":
					Config.LearningAlgorithm algorithm;
					String algorithmName = jExecution.optString("algorithm");
					try {
						algorithm = Config.LearningAlgorithm.valueOf(algorithmName);
					}
					catch(Exception ex){
						throw new StructureLearningException("Invalid algorithm " + algorithmName, ex);
					}
					switch (algorithm) {
						case SaiyanH:
							configurableExecution = new SaiyanHConfigurer(executor.getConfig()).configureFromJson(jExecution);
							break;
						case HC:
							configurableExecution = new HcConfigurer(executor.getConfig()).configureFromJson(jExecution);
							break;
						case GES:
							configurableExecution = new GesConfigurer(executor.getConfig()).configureFromJson(jExecution);
							break;
						case MAHC:
							configurableExecution = new MahcConfigurer(executor.getConfig()).configureFromJson(jExecution);
							break;
						case TABU:
							configurableExecution = new TabuConfigurer(executor.getConfig()).configureFromJson(jExecution);
							break;
						default:
							throw new StructureLearningException("Invalid algorithm " + algorithmName);
					}
					break;
				case "evaluation":
					configurableExecution = new EvaluationConfigurer(executor.getConfig()).configureFromJson(jExecution);
					break;
				default:
					throw new StructureLearningException("Invalid execution type: " + executionType);
			}
			
			if (executionType.equals("evaluation")){
				executor.getConfig().setPathOutput(executor.getOutputDirPath().toString());
				if (!jExecution.has("evaluationDataPath")){
					BLogger.logConditional("Specific evaluation data set not provided, using training data set for evaluation");
					executor.getConfig().setFileInputTrainingDataCsv(executor.getDataFilePath().getFileName().toString());
					executor.getConfig().setPathInput(executor.getDataFilePath().getParent().toString());
				}
				
				for (String modelFilePrefix: discoveryPrefixes.keySet()){
					Evaluation evaluation = new Evaluation();
					executor.getResult().getEvaluations().add(evaluation);
					evaluation.setModelLabel(discoveryPrefixes.get(modelFilePrefix));
					evaluation.setLabel(executionLabel);
					try {
						Path modelPath = executor.getOutputDirPath().resolve(modelFilePrefix + ".cmpx");
						Path csvPath = executor.getOutputDirPath().resolve(modelFilePrefix + ".csv");
						executor.getConfig().setFileOutputDagLearnedCsv(csvPath.getFileName().toString());
						if (!Files.exists(csvPath)){
							// Need to generate structure CSV from CMPX
							if (!Files.exists(modelPath)){
								String message = "Model file missing, can't evaluate: " + modelPath.toString();
								BLogger.logConditional(message);
								evaluation.setSuccess(false);
								evaluation.setMessage(message);
								continue;
							}

							CsvWriter.writeCsv(CmpxStructureExtractor.extract(Model.loadModel(modelPath.toString())), csvPath);
							csvPath.toFile().deleteOnExit();
						}
						configurableExecution.apply().execute();

						evaluation.setSuccess(true);
						evaluation.setBicScore(executor.getConfig().getCache().getBicScore());
						evaluation.setLogLikelihoodScore(executor.getConfig().getCache().getLogLikelihoodScore());
						evaluation.setComplexityScore(executor.getConfig().getCache().getComplexityScore());
						evaluation.setFreeParameters(executor.getConfig().getCache().getFreeParameters());
					}
					catch (Exception ex){
						String message = "Failed to evaluate " + modelFilePrefix+".cmpx: " + ex.getMessage();
						BLogger.logConditional(message);
						evaluation.setSuccess(false);
						evaluation.setMessage(message);
					}
				}
			}
			
			if (executionType.equals("discovery")){
				Discovery discovery = new Discovery();
				executor.getResult().getDiscoveries().add(discovery);
				executor.createTempDirs(jConfig);
				try {
					Files.createDirectories(executor.getInputDirPath());
					Files.createDirectories(executor.getOutputDirPath());
					// Result directory for Bayesys is the temporary folder
					configurableExecution.getConfig().setPathOutput(executor.getInputDirPath().toString());
				}
				catch (Exception ex){
					String message = "Failed to create directory structure: " + ex.getMessage();
					BLogger.logConditional(message);
					discovery.setSuccess(false);
					discovery.setMessage(message);
				}

				String modelFilePrefix = "model_" + jExecution.optString("algorithm","") + "_" + iExecution;
				if (executionLabel == null || executionLabel.isEmpty()){
					executionLabel = modelFilePrefix;
				}
				discoveryPrefixes.put(modelFilePrefix, executionLabel);
				
				discovery.setAlgorithm(jExecution.optString("algorithm",""));
				discovery.setLabel(executionLabel);
				discovery.setModelFilePrefix(modelFilePrefix);
				
				configurableExecution.getConfig().setFileOutputCmp(modelFilePrefix+".cmp");
				
				configurableExecution.apply().execute();
				
				try {
					Model model = Model.loadModel(configurableExecution.getConfig().getPathOutput().resolve(modelFilePrefix+".cmp").toString());
					// Now we save the cmpx version to the desired result location
					String modelFilePathString = executor.getOutputDirPath().resolve(modelFilePrefix+".cmpx").toString();
					model.save(executor.getOutputDirPath().resolve(modelFilePrefix+".cmpx").toString());
					Files.copy(
							executor.getConfig().getPathOutput().resolve(executor.getConfig().getFileOutputDagLearnedCsv()),
							executor.getOutputDirPath().resolve(modelFilePrefix+".csv"),
							StandardCopyOption.REPLACE_EXISTING
					);
					discovery.setModelPath(modelFilePathString);
					discovery.setModel(model.toJson().optJSONObject("model"));
					discovery.setSuccess(true);
				}
				catch(Exception ex){
					String message = "Failed to load discovered structure from file: " + ex.getMessage();
					BLogger.logConditional(message);
					discovery.setSuccess(false);
					discovery.setMessage(message);
				}
			}
		}
		
		return executor;
	}
	
}
