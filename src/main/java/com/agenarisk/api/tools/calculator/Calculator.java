package com.agenarisk.api.tools.calculator;

import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Observation;
import com.agenarisk.api.tools.Utils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.util.Logger;

/**
 * This class is for CLI API interface to enable calculations without using the Java API and relying on CLI invocation only
 * 
 * @author Eugene Dementiev
 */
public class Calculator {
	private Path pathModel = null;
	private Path pathDataSets = null;
	private Path pathOut = null;
	private boolean useCache = false;
	private boolean exitOnError = true;
	
	private Model model;
	
	private final HashSet<String> calculatedIds = new HashSet<>();
	private JSONArray jResults = new JSONArray();
	
	public Calculator(){}
	
	public Calculator withModel(String path){
		pathModel = Utils.resolve(path);
		try {
			model = Model.loadModel(pathModel.toString());
			model.getDataSetList().forEach(ds -> model.removeDataSet(ds));
			Logger.out().println("Model: " + pathModel);
		}
		catch (ModelException ex){
			throw new CalculatorException("Failed to load model from file", ex);
		}
		return this;
	}
	
	public Calculator withData(String path){
		pathDataSets = Utils.resolve(path);

		int i = 0;
		try {
			JSONArray jArray = Utils.readJsonArray(pathDataSets);
			for (; i < jArray.length(); i++) {
				JSONObject jDataSet = jArray.optJSONObject(i);
				model.createDataSet(jDataSet);
			}
		}
		catch (Exception ex){
			throw new CalculatorException("Failed to add data from input file at index " + i, ex);
		}
		
		Logger.out().println("Data: " + pathDataSets);
		
		return this;
	}
	
	public Calculator usingCache(boolean useCache){
		this.useCache = useCache;
		return this;
	}
	
	public Calculator exitOnError(boolean exitOnerror){
		this.exitOnError = exitOnerror;
		return this;
	}
	
	public Calculator savingTo(String path) {
		pathOut = Utils.resolve(path);
		Logger.out().println("Results: " + pathOut);
		return this;
	}
	
	public void execute(){
		Logger.out().println("Using cache: " + useCache);
		
		if (useCache){
			readCache();
		}
		
		model.getDataSets().values().stream()
				.filter(dataSet -> !calculatedIds.contains(dataSet.getId()))
				.forEach(dataSet -> {
					calculateDataset(dataSet);
				});
		
		Logger.out().println("All done");
	}
	
	private void calculateDataset(DataSet dataSet){
		Logger.log("Calculating: " + dataSet.getId());
		try {
			model.calculate(null, Arrays.asList(dataSet));
			writeResult(dataSet);
			Logger.log("Success");
			
		}
		catch (Exception ex){
			Logger.log("Failure: " + ex.getMessage());
			Logger.printThrowableIfDebug(ex);
			if (exitOnError){
				System.exit(1);
			}
		}
		catch (Error ex){
			Logger.log("Failure: " + ex.getMessage());
			Logger.printThrowableIfDebug(ex);
			if (exitOnError){
				System.exit(3);
			}
		}
	}
	
	private void writeResult(DataSet dataSet){
		try {
			JSONObject jResult = dataSet.toJson();
			jResult.remove(DataSet.Field.active.toString());
			jResult.remove(DataSet.Field.displayable.toString());
			jResult.remove(Observation.Field.observations.toString());
			jResults.put(jResult);
			Files.write(pathOut, jResults.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (Exception ex){
			throw new CalculatorException("Failed to write result for " + dataSet.getId(), ex);
		}
	}
	
	private void readCache(){
		try {
			jResults = Utils.readJsonArray(pathOut);
		}
		catch(Exception ex){
			throw new CalculatorException("Failed to read result cache from: " + pathOut, ex);
		}
		
		int i = 0;
		try {
			for(; i < jResults.length(); i++){
				calculatedIds.add(jResults.getJSONObject(i).getString("id"));
			}
			Logger.log("Read " + jResults.length() + " entries from cache");
		}
		catch (Exception ex){
			throw new CalculatorException("Result cache corrupted at index " + i, ex);
		}
	}
}
