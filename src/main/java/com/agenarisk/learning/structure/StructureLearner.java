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
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.execution.graph.GraphExecutor;
import com.agenarisk.learning.structure.execution.graph.GraphResult;
import com.agenarisk.learning.structure.execution.graph.WorkflowGraph;
import com.agenarisk.learning.structure.execution.graph.node.DataSourceNode;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import com.agenarisk.learning.structure.result.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

	private Path configDir = Paths.get("").toAbsolutePath();

	public void setConfigDir(Path dir) {
		this.configDir = (dir != null) ? dir.toAbsolutePath() : Paths.get("").toAbsolutePath();
	}

	private Path resolveConfigPath(String pathStr) {
		if (pathStr == null || pathStr.isEmpty()) {
			return configDir;
		}
		Path p = Paths.get(pathStr);
		return p.isAbsolute() ? p : configDir.resolve(p);
	}
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
		
		try {
			uk.co.agena.minerva.model.Model.TOOL_ON = true;
			executeJson(json);
		}
		catch (RuntimeException ex){
			throw ex;
		}
		finally {
			uk.co.agena.minerva.model.Model.TOOL_ON = false;
		}
	}
	
	public void executeJson(JSONObject json){
		boolean hasGraph = json.has("graph");
		boolean hasPipeline = json.has("pipeline");

		if (hasGraph && hasPipeline) {
			throw new StructureLearningException("Config must not contain both 'graph' and 'pipeline' keys");
		}

		if (hasGraph) {
			executeGraph(json);
		} else {
			executePipeline(json);
		}
	}

	private void executePipeline(JSONObject json) {
		JSONObject jExec = new JSONObject(json.toString());
		if (jExec.has("dataFilePath")) {
			jExec.put("dataFilePath", resolveConfigPath(jExec.getString("dataFilePath")).toString());
		}
		if (jExec.has("outputDirPath") && !jExec.optString("outputDirPath", "").isEmpty()) {
			jExec.put("outputDirPath", resolveConfigPath(jExec.getString("outputDirPath")).toString());
		}
		ConfiguredExecutor executor = ConfiguredExecutor.executeFromJson(jExec);

		boolean printSummary = json.optBoolean("printSummary", false);
		boolean saveSummary = json.optBoolean("saveSummary", false);
		boolean saveResult = json.optBoolean("saveResult", false);
		if (printSummary || saveSummary){
			List<Object> headers = Arrays.asList(
					"Discovery label",
					"Discovery success",
					"Algorithm",
					"Model file prefix",
					"Structure evaluation label",
					"Structure evaluation success",
					"BIC score",
					"LL score",
					"Complexity score",
					"Free parameters",
					"Performance evaluation label",
					"Performance evaluation success",
					"Absolute error",
					"Brier score",
					"Spherical score",
					"Macro AUC",
					"Micro AUC",
					"Performance evaluation message",
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

	private void executeGraph(JSONObject json) {
		Path outputDirPath;
		try {
			outputDirPath = resolveConfigPath(json.getString("outputDirPath"));
			Files.createDirectories(outputDirPath);
		} catch (Exception ex) {
			throw new StructureLearningException("Failed to resolve or create outputDirPath", ex);
		}

		WorkflowGraph graph;
		try {
			graph = WorkflowGraph.fromJson(json.getJSONObject("graph"));
		} catch (StructureLearningException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new StructureLearningException("Failed to parse graph", ex);
		}

		GraphExecutionContext ctx = new GraphExecutionContext(outputDirPath, configDir, graph.getNodesByLabel());

		if (json.optBoolean("bundle", false)) {
			try {
				Files.write(outputDirPath.resolve("input.json"), json.toString(2).getBytes(),
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				for (GraphNode node : graph.topologicalOrder()) {
					if (node instanceof DataSourceNode) {
						DataSourceNode dsNode = (DataSourceNode) node;
						Path src = dsNode.resolvedPath(ctx);
						Path dest = outputDirPath.resolve(dsNode.getLabel() + ".csv");
						if (!dest.equals(src)) {
							Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
						}
					}
				}
			} catch (Exception ex) {
				throw new StructureLearningException("Failed to create bundle", ex);
			}
		}

		GraphExecutor.execute(graph, ctx);

		boolean printSummary = json.optBoolean("printSummary", false);
		boolean saveSummary = json.optBoolean("saveSummary", false);
		boolean saveResult = json.optBoolean("saveResult", false);

		String graphLabel = json.optString("label", "");
		String graphDescription = json.optString("description", "");
		String graphVersion = json.optString("version", "");
		GraphResult result = new GraphResult(graphLabel, graphDescription, graphVersion,
				new ArrayList<>(graph.topologicalOrder()));

		if (printSummary || saveSummary) {
			List<Object> headers = Arrays.asList("Label", "Type", "SubType", "Status", "Status message");
			ArrayList<List<Object>> lines = new ArrayList<>(result.getSummary());
			lines.add(0, headers);

			if (printSummary) {
				lines.stream().map(line -> line.stream().map(el -> el + "").collect(Collectors.joining("\t"))).forEach(System.out::println);
			}

			if (saveSummary) {
				try {
					CsvWriter.writeCsv(lines, outputDirPath.resolve("summary.csv"));
				} catch (Exception ex) {
					throw new StructureLearningException("Failed to write summary to file", ex);
				}
			}
		}

		if (saveResult) {
			try {
				Files.write(outputDirPath.resolve("result.json"), result.toJson().toString().getBytes(),
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (Exception ex) {
				throw new StructureLearningException("Failed to write result to file", ex);
			}
		}
	}
}
