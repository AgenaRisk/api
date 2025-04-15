package com.agenarisk.learning.structure.execution;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.api.util.TempDirCleanup;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.ApplicableConfigurer;
import com.agenarisk.learning.structure.config.AveragingConfigurer;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.EvaluationConfigurer;
import com.agenarisk.learning.structure.config.GenerationConfigurer;
import com.agenarisk.learning.structure.config.GenerationExecutor;
import com.agenarisk.learning.structure.config.GesConfigurer;
import com.agenarisk.learning.structure.config.HcConfigurer;
import com.agenarisk.learning.structure.config.MahcConfigurer;
import com.agenarisk.learning.structure.config.MergerConfigurer;
import com.agenarisk.learning.structure.config.MergerExecutor;
import com.agenarisk.learning.structure.config.SaiyanHConfigurer;
import com.agenarisk.learning.structure.config.TableLearningConfigurer;
import com.agenarisk.learning.structure.config.TableLearningExecutor;
import com.agenarisk.learning.structure.config.TabuConfigurer;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.result.Discovery;
import com.agenarisk.learning.structure.execution.result.StructureEvaluation;
import com.agenarisk.learning.structure.execution.result.Result;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.utility.CmpxStructureExtractor;
import com.agenarisk.learning.structure.utility.ModelFromCsvCreator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.util.io.MinervaProperties;

/**
 *
 * @author Eugene Dementiev
 */
public class ConfiguredExecutor {
	private static final Pattern INVALID_LABEL_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]|(\\.\\.)|[/]");
	
	public static enum Stage {
		generation, discovery, averaging, evaluation, merger, tableLearning
	}
	
	private ArrayList<ArrayList<String>> data = null;
	private Path dataFilePath = null;
	private Path outputDirPath = null;
	private Path inputDirPath = null;
	
	private final Result result = new Result();
	
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
	}
	
	public static ConfiguredExecutor executeFromJson(JSONObject jConfig){
		HashMap<String, String> modelPrefixes = new HashMap<>();
		ConfiguredExecutor executor = new ConfiguredExecutor(Config.getInstance());
		
		try {
			executor.setDataFilePath(Paths.get(jConfig.optString("dataFilePath")));
			executor.setInputDirPath(executor.getDataFilePath().getParent());
		}
		catch (Exception ex){
			throw new StructureLearningException("Failed to read dataFilePath from JSON", ex);
		}
		
		try {
			Files.createDirectories(executor.getInputDirPath());
			executor.setOutputDirPath(Paths.get(jConfig.optString("outputDirPath", "")));
			Files.createDirectories(executor.getOutputDirPath());
		}
		catch (Exception ex){
			String message = "Failed to create directory structure: " + ex.getMessage();
			throw new StructureLearningException(message, ex);
		}
		
		if (jConfig.has("data")){
			throw new NotImplementedException("Data in JSON not yet supported");
		}
		
		JSONArray jPipeline = jConfig.optJSONArray("pipeline");
		if (jPipeline == null){
			// Nothing to do
			return executor;
		}

		for(int iStage = 0; iStage < jPipeline.length(); iStage++){
			JSONObject jStage = jPipeline.optJSONObject(iStage);
			if (jStage == null){
				continue;
			}
			
			executor.setConfig(Config.reset((config) -> {
				TempFileCleanup.cleanup(config);
				Database.reset();
			}));
			ApplicableConfigurer configurablePipeline;

			String stageType = jStage.optString("stage");
			String stageLabel = jStage.optString("label");
			
			executor.getConfig().setFileInputTrainingDataCsv(executor.getDataFilePath().getFileName().toString());
			executor.setInputDirPath(executor.getDataFilePath().getParent());
			executor.getConfig().setPathInput(executor.getInputDirPath().toString());
			
			switch (stageType) {
				case "discovery":
					Config.LearningAlgorithm algorithm;
					String algorithmName = jStage.optString("algorithm");
					try {
						algorithm = Config.LearningAlgorithm.valueOf(algorithmName);
					}
					catch(Exception ex){
						throw new StructureLearningException("Invalid algorithm " + algorithmName, ex);
					}
					switch (algorithm) {
						case SaiyanH:
							configurablePipeline = new SaiyanHConfigurer(executor.getConfig()).configureFromJson(jStage);
							break;
						case HC:
							configurablePipeline = new HcConfigurer(executor.getConfig()).configureFromJson(jStage);
							break;
						case GES:
							configurablePipeline = new GesConfigurer(executor.getConfig()).configureFromJson(jStage);
							break;
						case MAHC:
							configurablePipeline = new MahcConfigurer(executor.getConfig()).configureFromJson(jStage);
							break;
						case TABU:
							configurablePipeline = new TabuConfigurer(executor.getConfig()).configureFromJson(jStage);
							break;
						default:
							throw new StructureLearningException("Invalid algorithm " + algorithmName);
					}
					break;
				case "structureEvaluation":
					configurablePipeline = new EvaluationConfigurer(executor.getConfig()).configureFromJson(jStage);
					break;
				case "averaging":
					configurablePipeline = new AveragingConfigurer(executor.getConfig()).configureFromJson(jStage);
					break;
				case "generation":
					configurablePipeline = new GenerationConfigurer(executor.getConfig()).configureFromJson(jStage);
					break;
				case "merger":
					configurablePipeline = new MergerConfigurer(executor.getConfig());
					break;
				case "tableLearning":
					configurablePipeline = new TableLearningConfigurer(executor.getConfig()).configureFromJson(jStage);
					break;
				default:
					throw new StructureLearningException("Invalid stage type: " + stageType);
			}
			
			if (stageType.equals("generation")){
				executor.createTempDirs(jConfig);
				GenerationConfigurer genConfigurer = (GenerationConfigurer) configurablePipeline;
				Discovery discovery = new Discovery();
				executor.getResult().getDiscoveries().add(discovery);

				String modelFilePrefix = "model_generated" + "_" + iStage;
				if (stageLabel == null || stageLabel.isEmpty()){
					stageLabel = modelFilePrefix;
				}
				modelPrefixes.put(modelFilePrefix, stageLabel);
				
				discovery.setAlgorithm(jStage.optString("generation-" + genConfigurer.getStrategy().name(), ""));
				discovery.setLabel(stageLabel);
				discovery.setModelFilePrefix(modelFilePrefix);
				
				genConfigurer.setModelPath(executor.getOutputDirPath().resolve(modelFilePrefix+".cmpx"));
				
				try {
					GenerationExecutor genExecutor = (GenerationExecutor) configurablePipeline.apply();
					genExecutor.setOriginalConfigurer(genConfigurer);
					genExecutor.execute();
					discovery.setModelPath(genConfigurer.getModelPath().toString());
					discovery.setModel(genConfigurer.getModel().toJson().optJSONObject("model"));
					discovery.setSuccess(true);
				}
				catch(Exception ex){
					String message = "Failed to generate model: " + ex.getMessage();
					BLogger.logThrowableIfDebug(ex);
					discovery.setSuccess(false);
					discovery.setMessage(message);
				}
			}
			
			if (stageType.equals("tableLearning")){
				TableLearningConfigurer configurer = (TableLearningConfigurer) configurablePipeline;
				Discovery targetDiscovery = executor.getResult().getDiscoveries().stream().filter(discovery -> Objects.equals(configurer.getModelStageLabel(), discovery.getLabel())).findAny().orElse(null);
				if (targetDiscovery == null){
					BLogger.logConditional("No model with label found: " + configurer.getModelStageLabel());
					continue;
				}

				configurer.setModelPrefix(targetDiscovery.getModelFilePrefix());
				configurer.setModelPath(executor.getOutputDirPath().resolve(targetDiscovery.getModelFilePrefix()+".cmpx"));
				
				try {
					configurer.setModel(Model.createModel(targetDiscovery.getModel()));
					TableLearningExecutor stageExecutor = (TableLearningExecutor) configurablePipeline.apply();
					stageExecutor.setOriginalConfigurer(configurer);
					
					if (targetDiscovery.getAlgorithm().startsWith("generation-")){
						// Model was generated from variable names, which means it has bogus variable states
					}
					
					stageExecutor.execute();
					targetDiscovery.setModel(configurer.getModel().toJson().optJSONObject("model"));
				}
				catch(Exception ex){
					String message = "Failed to learn tables: " + ex.getMessage();
					BLogger.logThrowableIfDebug(ex);
					BLogger.logConditional(message);
				}
			}
			
			if (stageType.equals("structureEvaluation")){
				executor.getConfig().setPathOutput(executor.getOutputDirPath().toString());
				if (!jStage.has("evaluationDataPath")){
					BLogger.logConditional("Specific evaluation data set not provided, using training data set for evaluation");
					executor.getConfig().setFileInputTrainingDataCsv(executor.getDataFilePath().getFileName().toString());
					executor.getConfig().setPathInput(executor.getDataFilePath().getParent().toString());
				}
				
				for (String modelFilePrefix: modelPrefixes.keySet()){
					StructureEvaluation evaluation = new StructureEvaluation();
					executor.getResult().getStructureEvaluations().add(evaluation);
					evaluation.setModelLabel(modelPrefixes.get(modelFilePrefix));
					evaluation.setLabel(stageLabel);
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
						configurablePipeline.apply().execute();

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
			
			if (stageType.equals("discovery")){
				Discovery discovery = new Discovery();
				executor.getResult().getDiscoveries().add(discovery);
				executor.createTempDirs(jConfig);
				// Result directory for Bayesys is the temporary folder
				configurablePipeline.getConfig().setPathOutput(executor.getInputDirPath().toString());

				String modelFilePrefix = "model_" + jStage.optString("algorithm","") + "_" + iStage;
				if (stageLabel == null || stageLabel.isEmpty()){
					stageLabel = modelFilePrefix;
				}
				modelPrefixes.put(modelFilePrefix, stageLabel);
				
				discovery.setAlgorithm(jStage.optString("algorithm",""));
				discovery.setLabel(stageLabel);
				discovery.setModelFilePrefix(modelFilePrefix);
				
				configurablePipeline.getConfig().setFileOutputCmp(modelFilePrefix+".cmp");
				
				configurablePipeline.apply().execute();
				
				try {
					Path cmpModelPath = configurablePipeline.getConfig().getPathOutput().resolve(modelFilePrefix+".cmp");
					Model model = Model.loadModel(cmpModelPath.toString());
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
					if (Boolean.parseBoolean(MinervaProperties.getProperty("com.agenarisk.learning.structure.deleteTransientFiles", "true"))){
						TempFileCleanup.registerTempFile(cmpModelPath.toFile(), executor.getConfig());
						TempFileCleanup.registerTempFile(configurablePipeline.getConfig().getPathOutput().resolve("CPDAGlearned.csv").toFile(), executor.getConfig());
						TempFileCleanup.registerTempFile(configurablePipeline.getConfig().getPathOutput().resolve("DAGlearned.csv").toFile(), executor.getConfig());
					}
				}
				catch(Exception ex){
					String message = "Failed to load discovered structure from file: " + ex.getMessage();
					BLogger.logConditional(message);
					discovery.setSuccess(false);
					discovery.setMessage(message);
				}
			}
			
			if (stageType.equals("averaging")){
				executor.getConfig().setPathInput(executor.getOutputDirPath().toString());
				executor.getConfig().setPathOutput(executor.getOutputDirPath().toString());
				List<List<Object>> lines = new ArrayList<>();
				lines.add(Arrays.asList("ID", "Variable 1", "Dependency", "Variable 2"));
				
				Discovery discovery = new Discovery();
				executor.getResult().getDiscoveries().add(discovery);
				String avgPrefix = "model_average_" + iStage;
				
				if (stageLabel == null || stageLabel.isEmpty()){
					stageLabel = avgPrefix;
				}
				
				discovery.setLabel(stageLabel);
				discovery.setAverage(true);
				discovery.setAlgorithm(jStage.optString("algorithm","averaging"));
				
				for (String modelFilePrefix: modelPrefixes.keySet()){
					try {
						Path modelPath = executor.getOutputDirPath().resolve(modelFilePrefix + ".cmpx");
						Path csvPath = executor.getOutputDirPath().resolve(modelFilePrefix + ".csv");
						
						if (!Files.exists(csvPath)){
							// Need to generate structure CSV from CMPX
							if (!Files.exists(modelPath)){
								String message = "Model file missing, can't use in average: " + modelPath.toString();
								BLogger.logConditional(message);
								discovery.setSuccess(false);
								discovery.setMessage(message);
								continue;
							}
						}
						
						lines.addAll(CmpxStructureExtractor.extract(Model.loadModel(modelPath.toString()), null));
					}
					catch (Exception ex){
						String message = "Failed to add model to average " + modelFilePrefix+".cmpx: " + ex.getMessage();
						BLogger.logConditional(message);
					}
				}
				
				try {
					Path csvInput = executor.getOutputDirPath().resolve(Config.FILE_AVERAGING_INPUT);
					CsvWriter.writeCsv(lines, csvInput);
					csvInput.toFile().deleteOnExit();
					configurablePipeline.apply().execute();
					
					Path csvOutput = executor.getOutputDirPath().resolve(Config.FILE_AVERAGING_OUTPUT);
					csvOutput.toFile().deleteOnExit();

					Model model = ModelFromCsvCreator.create(CsvReader.readCsv(csvOutput), discovery.getModelFilePrefix(), discovery.getLabel());
					
					String modelFilePathString = executor.getOutputDirPath().resolve(avgPrefix+".cmpx").toString();
					model.save(modelFilePathString);
					Files.copy(
							csvOutput,
							executor.getOutputDirPath().resolve(avgPrefix+".csv"),
							StandardCopyOption.REPLACE_EXISTING
					);
//					System.out.println("Save model to: "+modelFilePathString);
//					System.out.println("Save dag csv to: "+executor.getOutputDirPath().resolve(avgPrefix+".csv"));
					discovery.setModelPath(modelFilePathString);
					discovery.setModel(model.toJson().optJSONObject("model"));
					discovery.setModelFilePrefix(avgPrefix);
					discovery.setSuccess(true);
					modelPrefixes.put(avgPrefix, stageLabel);
				}
				catch(Exception ex){
					String message = "Failed to produce average structure: " + ex.getMessage();
					BLogger.logConditional(message);
					discovery.setSuccess(false);
					discovery.setMessage(message);
					BLogger.logThrowableIfDebug(ex);
				}
			}
			
			if (stageType.equals("merger")){
				executor.createTempDirs(jConfig);
				MergerConfigurer mergerConfigurer = (MergerConfigurer) configurablePipeline;
				String modelFilePrefix = "model_merged" + "_" + iStage;
//				if (stageLabel == null || stageLabel.isEmpty()){
//					stageLabel = modelFilePrefix;
//				}
				
				mergerConfigurer.setInputDirPath(executor.getOutputDirPath());
				mergerConfigurer.setOutputDirPath(executor.getOutputDirPath());
				mergerConfigurer.setModelPrefix(modelFilePrefix);
				mergerConfigurer.setModelPrefixes(Collections.unmodifiableMap(modelPrefixes));
				
				MergerExecutor mergerExecutor = mergerConfigurer.apply();
				mergerExecutor.setOriginalConfigurer(mergerConfigurer);
				mergerExecutor.execute();
			}
		}
		
		return executor;
	}
	
}
