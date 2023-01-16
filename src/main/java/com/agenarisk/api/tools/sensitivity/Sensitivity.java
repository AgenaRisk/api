package com.agenarisk.api.tools.sensitivity;

import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.tools.SensitivityAnalyser;
import com.agenarisk.api.tools.SensitivityAnalyserException;
import com.agenarisk.api.tools.Utils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.util.Logger;

/**
 * This class is for CLI API interface to enable calculations without using the Java API and relying on CLI invocation only
 * 
 * @author Eugene Dementiev
 */
public class Sensitivity {
	private Path pathModel = null;
	private Path filePath = null;
	private Path pathOut = null;
	
	private Model model;
	private JSONObject config;
	
	public Sensitivity(){}
	
	public Sensitivity withModel(String path){
		pathModel = Utils.resolve(path);
		try {
			model = Model.loadModel(pathModel.toString());
			model.getDataSetList().forEach(ds -> model.removeDataSet(ds));
			Logger.out().println("Model: " + pathModel);
		}
		catch (ModelException ex){
			throw new SensitivityException("Failed to load model from file", ex);
		}
		return this;
	}
	
	public Sensitivity withData(String path){
		Logger.out().println("Overriding data in model with data from: " + path);
		
		// Remove all existing data sets
		model.getDataSetList().forEach(ds -> {
			model.removeDataSet(ds);
		});
		
		filePath = Utils.resolve(path);
		
		JSONArray jArray;
		try {
			jArray = Utils.readJsonArray(filePath);
		}
		catch (Exception ex){
			throw new SensitivityException("Failed to read data file: " + path, ex);
		}

		int i = 0;
		try {
			for (; i < jArray.length(); i++) {
				JSONObject jDataSet = jArray.optJSONObject(i);
				model.createDataSet(jDataSet);
			}
		}
		catch (Exception ex){
			throw new SensitivityException("Failed to add data from input file at index " + i, ex);
		}
		
		Logger.out().println("Data: " + filePath);
		
		return this;
	}
	
	public Sensitivity withConfig(String path){
		filePath = Utils.resolve(path);

		config = Utils.readJsonObject(filePath);
		
		Logger.out().println("Loaded config: " + filePath);
		
		return this;
	}
	
	public Sensitivity savingTo(String path) {
		pathOut = Utils.resolve(path);
		Logger.out().println("Results: " + pathOut);
		return this;
	}
	
	public void execute(){
		try {
			SensitivityAnalyser sa = new SensitivityAnalyser(model, config);
			JSONObject jsonReport = sa.getFullReport();
			writeResult(jsonReport);
		}
		catch (SensitivityAnalyserException ex){
			throw new SensitivityException("Failed to carry out sensitivity analysis", ex);
		}
		
	}
	
	private void writeResult(JSONObject json){
		try {
			Files.write(pathOut, json.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (Exception ex){
			throw new SensitivityException("Failed to write result to: " + pathOut, ex);
		}
	}
	
}
