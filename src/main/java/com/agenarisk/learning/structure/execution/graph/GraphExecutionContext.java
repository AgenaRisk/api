package com.agenarisk.learning.structure.execution.graph;

import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import java.nio.file.Path;
import java.util.Map;

public class GraphExecutionContext {

	private final Path outputDirPath;
	private final Map<String, GraphNode> nodesByLabel;

	public GraphExecutionContext(Path outputDirPath, Map<String, GraphNode> nodesByLabel) {
		this.outputDirPath = outputDirPath;
		this.nodesByLabel = nodesByLabel;
	}

	public Path getOutputDirPath() {
		return outputDirPath;
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
