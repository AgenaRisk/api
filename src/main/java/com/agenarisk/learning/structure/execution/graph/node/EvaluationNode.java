package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.logger.BLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public abstract class EvaluationNode extends GraphNode {

	protected boolean outputToFile = false;
	protected String outputFileName = "";

	public Path getEvalPath(GraphExecutionContext ctx) {
		return ctx.getOutputDirPath().resolve(getLabel() + ".json");
	}

	protected void parseOutputFileOptions(JSONObject jOptions) {
		outputToFile = jOptions.optBoolean("outputToFile", false);
		outputFileName = jOptions.optString("outputFileName", "");
	}

	protected abstract List<List<Object>> toCsvRows(JSONArray results);

	protected void writeOutputFileIfRequested(GraphExecutionContext ctx, JSONArray results) {
		if (!outputToFile) return;
		String fileName = (outputFileName != null && !outputFileName.isEmpty()) ? outputFileName : (getLabel() + ".csv");
		Path outputPath = ctx.getOutputDirPath().resolve(fileName);
		try {
			if (fileName.toLowerCase().endsWith(".csv")) {
				CsvWriter.writeCsv(toCsvRows(results), outputPath);
			} else {
				Files.write(outputPath, results.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
		} catch (Exception ex) {
			BLogger.logConditional("Failed to write evaluation output file '" + fileName + "': " + ex.getMessage());
			BLogger.logThrowableIfDebug(ex);
		}
	}
}
