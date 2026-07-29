package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.api.util.CsvReader;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import org.json.JSONObject;

public class DataSourceNode extends GraphNode {

	private String path;
	private String date;
	private String version;

	@Override
	public String getSubType() {
		return "dataSource";
	}

	@Override
	protected void parseNodeFields(JSONObject jNode) {
		this.date = jNode.optString("date", LocalDate.now().toString());
		this.version = jNode.optString("version", "");
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.path = jOptions.optString("path", "");
	}

	@Override
	public Set<String> getInputLabels() {
		return Collections.emptySet();
	}

	@Override
	public void execute(GraphExecutionContext ctx) {
		try {
			if (path == null || path.isEmpty()) {
				throw new IllegalArgumentException("Data source path is required");
			}
			Path filePath = ctx.resolveConfigPath(path);
			if (!Files.exists(filePath)) {
				throw new IllegalArgumentException("Data file not found: " + filePath);
			}
			if (Files.isDirectory(filePath)) {
				throw new IllegalArgumentException("Path points to a directory, not a file: " + filePath);
			}
			if (!Files.isReadable(filePath)) {
				throw new IllegalArgumentException("Data file is not readable: " + filePath);
			}
			CsvReader.readHeaders(filePath);

			JSONObject result = new JSONObject();
			result.put("path", filePath.toAbsolutePath().toString());
			result.put("description", getDescription());
			result.put("date", date);
			result.put("version", version);
			setResult(result);
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Failed to load data source: " + friendlyMessage(ex), ex);
		}
	}

	public String getPath() {
		return path;
	}

	public String getDate() {
		return date;
	}

	public String getVersion() {
		return version;
	}

	public Path resolvedPath(GraphExecutionContext ctx) {
		return ctx.resolveConfigPath(path);
	}
}
