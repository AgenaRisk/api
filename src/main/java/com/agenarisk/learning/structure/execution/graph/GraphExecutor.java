package com.agenarisk.learning.structure.execution.graph;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.execution.graph.node.DataSourceNode;
import com.agenarisk.learning.structure.execution.graph.node.EvaluationNode;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelMergeNode;
import com.agenarisk.learning.structure.execution.graph.node.ModelNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;

public class GraphExecutor {

	public static void execute(WorkflowGraph graph, GraphExecutionContext ctx) {
		for (GraphNode node : graph.topologicalOrder()) {
			String inputError = checkInputs(node, ctx);
			if (inputError != null) {
				node.failWith(inputError, null);
				continue;
			}

			if (node.isUseCache() && tryLoadFromCache(node, ctx)) {
				continue;
			}

			node.execute(ctx);
		}
	}

	private static String checkInputs(GraphNode node, GraphExecutionContext ctx) {
		boolean hasHardInputs = false;
		boolean hasUsableHard = false;
		boolean hasSoftInputs = false;
		boolean hasUsableSoft = false;

		for (String inputLabel : node.getInputLabels()) {
			GraphNode input = ctx.getNode(inputLabel);
			if (input == null) {
				continue;
			}
			boolean usable = input.getStatus() == GraphNode.Status.success || input.getStatus() == GraphNode.Status.warning;
			if (input instanceof DataSourceNode || input instanceof EvaluationNode) {
				hasHardInputs = true;
				if (usable) {
					hasUsableHard = true;
				}
			}
			else {
				hasSoftInputs = true;
				if (usable) {
					hasUsableSoft = true;
				}
			}
		}

		if (hasHardInputs && !hasUsableHard) {
			return "Required inputs (data/evaluation) are not available";
		}
		if (hasSoftInputs && !hasUsableSoft) {
			return "All model inputs failed";
		}
		return null;
	}

	private static boolean tryLoadFromCache(GraphNode node, GraphExecutionContext ctx) {
		try {
			if (node instanceof ModelNode || node instanceof ModelMergeNode) {
				Path cmpxPath = ctx.modelPath(node.getLabel());
				if (!Files.exists(cmpxPath)) {
					return false;
				}
				Model model = Model.loadModel(cmpxPath.toString());
				node.setResult(model.toJson().optJSONObject("model"));
				node.setStatus(GraphNode.Status.success);
				return true;
			}
			else if (node instanceof EvaluationNode) {
				Path jsonPath = ctx.evalPath(node.getLabel());
				if (!Files.exists(jsonPath)) {
					return false;
				}
				String content = new String(Files.readAllBytes(jsonPath));
				node.setResult(new JSONArray(content));
				node.setStatus(GraphNode.Status.success);
				return true;
			}
		}
		catch (Exception ex) {
			// Cache load failed — proceed with normal execution
		}
		return false;
	}
}
