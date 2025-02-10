package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempDirCleanup;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import org.apache.commons.lang3.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class ConfiguredExecutor {
	private ArrayList<ArrayList<String>> data = null;
	private Path dataFilePath = null;
	private Path outputDirPath = null;
	private Path inputDirPath = null;
	
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
	
	public static void executeFromJson(JSONObject jConfig){
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
			return;
		}
				
		for(int i = 0; i < jExecutions.length(); i++){
			JSONObject jExecution = jExecutions.optJSONObject(i);
			if (jExecution == null){
				continue;
			}
			
			executor.setConfig(Config.reset());
			BicLogConfigurer configurableExecution;

			String executionType = jExecution.optString("execution");
			
			if (executionType.equals("discovery")){
				// If we are using discovery, no point to copy the data file
				executor.getConfig().setFileInputTrainingDataCsv(executor.getDataFilePath().getFileName().toString());
				executor.setInputDirPath(executor.getDataFilePath().getParent());
			}
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
//					executioner.configureForEvaluation(jExecution);
					throw new NotImplementedException("Evaluation execution not implemented yet");
				default:
					throw new StructureLearningException("Invalid execution type: " + executionType);
			}
			
			executor.createTempDirs(jConfig);
			try {
				Files.createDirectories(executor.getInputDirPath());
				Files.createDirectories(executor.getOutputDirPath());
				// Output directory for Bayesys is the temporary folder
				configurableExecution.getConfig().setPathOutput(executor.getInputDirPath().toString());
			}
			catch (Exception ex){
				throw new StructureLearningException("Failed to create directory structure", ex);
			}
			
			String modelFilePrefix = "model_" + i;
			configurableExecution.getConfig().setFileOutputCmp(modelFilePrefix+".cmp");
			
//			System.out.println("Temp input dir: "+ executor.getConfig().getPathInput());
//			System.out.println("Data file:" + executor.getConfig().getFileInputTrainingDataCsv());
//			System.out.println("Temp output dir: "+executor.getConfig().getPathOutput());
//			System.out.println("Temp out file: "+executor.getConfig().getFileOutputCmp());
//			System.out.println("Output dir: "+executor.getOutputDirPath());
			
			configurableExecution.apply().execute();
			
			try {
				Model model = Model.loadModel(configurableExecution.getConfig().getPathOutput().resolve(modelFilePrefix+".cmp").toString());
				// Now we save the cmpx version to the desired output location
				model.save(executor.getOutputDirPath().resolve(modelFilePrefix+".cmpx").toString());
				Files.copy(
						executor.getConfig().getPathOutput().resolve(executor.getConfig().getFileOutputDagLearnedCsv()),
						executor.getOutputDirPath().resolve(modelFilePrefix+".csv"),
						StandardCopyOption.REPLACE_EXISTING
				);
			}
			catch(Exception ex){
				throw new StructureLearningException("Failed to load discovered structure from file", ex);
			}
		}
	}
	
}
