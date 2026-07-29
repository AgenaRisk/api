package com.agenarisk.learning.structure.execution.graph;

import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class GraphExecutionContext {

	private final Path outputDirPath;
	private final Path configDir;
	private final Map<String, GraphNode> nodesByLabel;
	private boolean progressOutput = false;

	public GraphExecutionContext(Path outputDirPath, Path configDir, Map<String, GraphNode> nodesByLabel) {
		this.outputDirPath = outputDirPath;
		this.configDir = configDir;
		this.nodesByLabel = nodesByLabel;
	}

	public boolean isProgressOutput() {
		return progressOutput;
	}

	public void setProgressOutput(boolean progressOutput) {
		this.progressOutput = progressOutput;
	}

	public Path getOutputDirPath() {
		return outputDirPath;
	}

	public Path getConfigDir() {
		return configDir;
	}

	public Path resolveConfigPath(String path) {
		if (path == null || path.isEmpty()) {
			return configDir;
		}
		Path p = Paths.get(path);
		return p.isAbsolute() ? p : configDir.resolve(p);
	}

	public GraphNode getNode(String label) {
		if (label == null) {
			return null;
		}
		return nodesByLabel.get(label.toLowerCase());
	}

	public Path modelPath(String label) {
		return outputDirPath.resolve(label + ".cmpx");
	}

	public Path evalPath(String label) {
		return outputDirPath.resolve(label + ".json");
	}
}
