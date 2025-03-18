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
import java.nio.file.Paths;
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
		
		boolean print = json.optBoolean("printSummary", false);
		boolean save = json.optBoolean("saveSummary", false);
		if (print || save){
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
			ArrayList<List<Object>> lines = executor.getResult().getSummary();
			lines.add(0, headers);
			
			if (print){
				lines.stream().map(line -> line.stream().map(el -> el+"").collect(Collectors.joining("\t"))).forEach(System.out::println);
			}
			
			if (save){
				try {
					CsvWriter.writeCsv(lines, Paths.get(executor.getOutputDirPath().resolve("summary.csv").toString()));
				}
				catch(Exception ex){
					throw new StructureLearningException("Failed to write summary to file", ex);
				}
			}
		}
	}
}
