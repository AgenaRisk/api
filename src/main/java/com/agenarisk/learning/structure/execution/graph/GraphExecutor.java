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
import org.json.JSONObject;

public class GraphExecutor {

	public static void execute(WorkflowGraph graph, GraphExecutionContext ctx) {
		for (GraphNode node : graph.topologicalOrder()) {
			String inputError = checkInputs(node, ctx);
			if (inputError != null) {
				node.failWith(inputError, null);
				if (ctx.isProgressOutput()) {
					emitNodeComplete(node, false);
				}
				continue;
			}

			if (ctx.isProgressOutput()) {
				GraphNode.emitProgress(new JSONObject()
					.put("type", "nodeStart")
					.put("nodeLabel", node.getLabel()));
			}

			if (node.isUseCache() && tryLoadFromCache(node, ctx)) {
				if (ctx.isProgressOutput()) {
					emitNodeComplete(node, true);
				}
				continue;
			}

			node.execute(ctx);

			if (ctx.isProgressOutput()) {
				emitNodeComplete(node, false);
			}
		}
	}

	private static void emitNodeComplete(GraphNode node, boolean useCache) {
		JSONObject event = new JSONObject()
			.put("type", "nodeComplete")
			.put("nodeLabel", node.getLabel())
			.put("status", node.getStatus().name())
			.put("useCache", useCache);
		if (!node.getStatusMessage().isEmpty()) {
			event.put("statusMessage", node.getStatusMessage());
		}
		GraphNode.emitProgress(event);
	}

	private static String checkInputs(GraphNode node, GraphExecutionContext ctx) {
		boolean hasHardInputs = false;
		boolean hasUsableHard = false;
		boolean hasSoftInputs = false;
		boolean hasUsableSoft = false;
		java.util.List<String> failedHard = new java.util.ArrayList<>();
		java.util.List<String> failedSoft = new java.util.ArrayList<>();

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
				else {
					failedHard.add(inputLabel);
				}
			}
			else {
				hasSoftInputs = true;
				if (usable) {
					hasUsableSoft = true;
				}
				else {
					failedSoft.add(inputLabel);
				}
			}
		}

		if (hasHardInputs && !hasUsableHard) {
			return "Cannot run '" + node.getLabel() + "': required input"
					+ (failedHard.size() > 1 ? "s" : "") + " did not produce a result ("
					+ String.join(", ", failedHard) + "). Fix the upstream node(s) and re-run.";
		}
		if (hasSoftInputs && !hasUsableSoft) {
			return "Cannot run '" + node.getLabel() + "': all connected model inputs failed ("
					+ String.join(", ", failedSoft) + "). Fix the upstream node(s) and re-run.";
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
