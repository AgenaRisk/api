package com.agenarisk.learning.structure;

import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.execution.ConfiguredExecutor;
import com.agenarisk.learning.structure.config.GesConfigurer;
import com.agenarisk.learning.structure.config.HcConfigurer;
import com.agenarisk.learning.structure.config.MahcConfigurer;
import com.agenarisk.learning.structure.config.SaiyanHConfigurer;
import com.agenarisk.learning.structure.config.TabuConfigurer;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.result.Result;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class StructureLearner {
	public SaiyanHConfigurer learnWithSaiyanH(){
		Config.reset();
		return new SaiyanHConfigurer(Config.getInstance());
	}
	
	public HcConfigurer learnWithHc(){
		Config.reset();
		return new HcConfigurer(Config.getInstance());
	}
	
	public MahcConfigurer learnWithMahc(){
		Config.reset();
		return new MahcConfigurer(Config.getInstance());
	}
	
	public GesConfigurer learnWithGes(){
		Config.reset();
		return new GesConfigurer(Config.getInstance());
	}
	
	public TabuConfigurer learnWithTabu(){
		Config.reset();
		return new TabuConfigurer(Config.getInstance());
	}
	
	public void executeJson(String jsonString){
		JSONObject json;
		try {
			json = new JSONObject(jsonString);
		}
		catch (Exception ex){
			throw new StructureLearningException("Failed to read config from JSON", ex);
		}
		
		executeJson(json);
	}
	
	public void executeJson(JSONObject json){
		ConfiguredExecutor executor = ConfiguredExecutor.executeFromJson(json);
		
		boolean printSummary = json.optBoolean("printSummary", false);
		boolean saveSummary = json.optBoolean("saveSummary", false);
		boolean saveResult = json.optBoolean("saveResult", false);
		if (printSummary || saveSummary){
			List<Object> headers = Arrays.asList(
					"Discovery Label",
					"Discovery Success",
					"Algorithm",
					"Model file prefix",
					"Evaluation Label",
					"Evaluation Success",
					"BIC Score",
					"LL Score",
					"Complexity Score",
					"Free Parameters",
					"Model path"
			);
			Result result = executor.getResult();
			ArrayList<List<Object>> lines = result.getSummary();
			lines.add(0, headers);
			
			if (printSummary){
				lines.stream().map(line -> line.stream().map(el -> el+"").collect(Collectors.joining("\t"))).forEach(System.out::println);
			}
			
			if (saveSummary){
				try {
					CsvWriter.writeCsv(lines, Paths.get(executor.getOutputDirPath().resolve("summary.csv").toString()));
				}
				catch(Exception ex){
					throw new StructureLearningException("Failed to write summary to file", ex);
				}
			}
			
			if (saveResult){
				try {
					Files.write(executor.getOutputDirPath().resolve("result.json"), result.toJson().toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				}
				catch(Exception ex){
					throw new StructureLearningException("Failed to write result to file", ex);
				}
			}
		}
	}
}
