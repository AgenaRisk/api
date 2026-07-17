package com.agenarisk.learning.structure.execution.graph;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.graph.node.DataSourceNode;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelAveragingNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelDiscoveryNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelGenerationNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelImportNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelMergeNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelSelectionNode;
import com.agenarisk.learning.structure.execution.graph.node.PerformanceEvaluationNode;
import com.agenarisk.learning.structure.execution.graph.node.ProbabilityLearningNode;
import com.agenarisk.learning.structure.execution.graph.node.RegressionParameterLearningNode;
import com.agenarisk.learning.structure.execution.graph.node.RegressionStructureLearningNode;
import com.agenarisk.learning.structure.execution.graph.node.StructureEvaluationNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class WorkflowGraph {

	private static final Pattern FORBIDDEN_LABEL_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\u0000-\\u001F]");

	private final Map<String, GraphNode> nodesByLabel;
	private final List<GraphNode> topoOrder;

	private WorkflowGraph(Map<String, GraphNode> nodesByLabel, List<GraphNode> topoOrder) {
		this.nodesByLabel = nodesByLabel;
		this.topoOrder = topoOrder;
	}

	public static WorkflowGraph fromJson(JSONObject jGraph) throws StructureLearningException {
		JSONArray jNodes = jGraph.optJSONArray("nodes");
		if (jNodes == null) {
			throw new StructureLearningException("Graph must have a 'nodes' array");
		}

		// Phase 1: Parse all node declarations
		Map<String, GraphNode> allNodes = new LinkedHashMap<>();
		Set<String> unknownTypeLabels = new LinkedHashSet<>();
		Set<String> allDeclaredLabels = new LinkedHashSet<>();

		for (int i = 0; i < jNodes.length(); i++) {
			JSONObject jNode = jNodes.optJSONObject(i);
			if (jNode == null) {
				continue;
			}

			String rawLabel = jNode.optString("label", "").trim();
			String type = jNode.optString("type", "");

			validateLabel(rawLabel);

			String lcLabel = rawLabel.toLowerCase();
			if (allDeclaredLabels.contains(lcLabel)) {
				throw new StructureLearningException("Duplicate label (case-insensitive): '" + rawLabel + "'");
			}
			allDeclaredLabels.add(lcLabel);

			GraphNode node = createNodeForType(type);
			if (node == null) {
				unknownTypeLabels.add(lcLabel);
				continue;
			}
			node.parseCommon(jNode);
			allNodes.put(lcLabel, node);
		}

		// Phase 2: Validate that all referenced input labels are declared
		for (GraphNode node : allNodes.values()) {
			for (String inputLabel : node.getInputLabels()) {
				if (!allDeclaredLabels.contains(inputLabel.toLowerCase())) {
					throw new StructureLearningException(
									"Node '" + node.getLabel() + "' references undefined label '" + inputLabel + "'");
				}
			}
		}

		// Phase 3: Transitively exclude unknown-type nodes and their descendants
		Set<String> excludedLabels = new LinkedHashSet<>(unknownTypeLabels);
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Map.Entry<String, GraphNode> entry : allNodes.entrySet()) {
				if (excludedLabels.contains(entry.getKey())) {
					continue;
				}
				for (String inputLabel : entry.getValue().getInputLabels()) {
					if (excludedLabels.contains(inputLabel.toLowerCase())) {
						excludedLabels.add(entry.getKey());
						changed = true;
						break;
					}
				}
			}
		}

		// Phase 4: Build final node map without excluded nodes
		Map<String, GraphNode> nodesByLabel = new LinkedHashMap<>();
		for (Map.Entry<String, GraphNode> entry : allNodes.entrySet()) {
			if (!excludedLabels.contains(entry.getKey())) {
				nodesByLabel.put(entry.getKey(), entry.getValue());
			}
		}

		// Phase 5: Topological sort with cycle detection (Kahn's algorithm)
		List<GraphNode> topoOrder = topologicalSort(nodesByLabel);

		return new WorkflowGraph(nodesByLabel, topoOrder);
	}

	private static void validateLabel(String label) throws StructureLearningException {
		if (label == null || label.isEmpty()) {
			throw new StructureLearningException("Node label must not be empty");
		}
		if (FORBIDDEN_LABEL_CHARS.matcher(label).find()) {
			throw new StructureLearningException(
							"Node label '" + label + "' contains characters not allowed in cross-platform file names");
		}
	}

	private static GraphNode createNodeForType(String type) {
		switch (type) {
			case "dataSource":
				return new DataSourceNode();
			case "modelGeneration":
				return new ModelGenerationNode();
			case "modelDiscovery":
				return new ModelDiscoveryNode();
			case "modelImport":
				return new ModelImportNode();
			case "probabilityLearning":
				return new ProbabilityLearningNode();
			case "regressionStructureDiscovery":
				return new RegressionStructureLearningNode();
			case "regressionParameterLearning":
				return new RegressionParameterLearningNode();
			case "performanceEvaluation":
				return new PerformanceEvaluationNode();
			case "structureEvaluation":
				return new StructureEvaluationNode();
			case "modelSelection":
				return new ModelSelectionNode();
			case "modelAveraging":
				return new ModelAveragingNode();
			case "modelMerge":
				return new ModelMergeNode();
			default:
				return null;
		}
	}

	private static List<GraphNode> topologicalSort(Map<String, GraphNode> nodes) throws StructureLearningException {
		Map<String, Integer> inDegree = new LinkedHashMap<>();
		Map<String, List<String>> dependents = new LinkedHashMap<>();

		for (String label : nodes.keySet()) {
			inDegree.put(label, 0);
			dependents.put(label, new ArrayList<>());
		}

		for (Map.Entry<String, GraphNode> entry : nodes.entrySet()) {
			String childLabel = entry.getKey();
			for (String inputLabel : entry.getValue().getInputLabels()) {
				String lcInput = inputLabel.toLowerCase();
				if (!nodes.containsKey(lcInput)) {
					continue;
				}
				dependents.get(lcInput).add(childLabel);
				inDegree.merge(childLabel, 1, Integer::sum);
			}
		}

		Queue<String> queue = new LinkedList<>();
		for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
			if (entry.getValue() == 0) {
				queue.add(entry.getKey());
			}
		}

		List<GraphNode> order = new ArrayList<>();
		while (!queue.isEmpty()) {
			String label = queue.poll();
			order.add(nodes.get(label));
			for (String dependent : dependents.get(label)) {
				if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
					queue.add(dependent);
				}
			}
		}

		if (order.size() != nodes.size()) {
			throw new StructureLearningException("Graph contains a cycle");
		}

		return order;
	}

	public List<GraphNode> topologicalOrder() {
		return Collections.unmodifiableList(topoOrder);
	}

	public GraphNode getNode(String label) {
		return label == null ? null : nodesByLabel.get(label.toLowerCase());
	}

	public Map<String, GraphNode> getNodesByLabel() {
		return Collections.unmodifiableMap(nodesByLabel);
	}
}
