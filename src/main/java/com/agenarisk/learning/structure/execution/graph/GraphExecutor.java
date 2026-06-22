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
			if (shouldSkip(node, ctx)) {
				node.setStatus(GraphNode.Status.skipped);
				node.setStatusMessage("Skipped due to failed or skipped dependencies");
				continue;
			}

			if (node.isUseCache() && tryLoadFromCache(node, ctx)) {
				continue;
			}

			node.execute(ctx);
		}
	}

	private static boolean shouldSkip(GraphNode node, GraphExecutionContext ctx) {
		boolean hasModelInputs = false;
		boolean allModelInputsFailed = true;

		for (String inputLabel : node.getInputLabels()) {
			GraphNode input = ctx.getNode(inputLabel);
			if (input == null) {
				continue;
			}

			GraphNode.Status s = input.getStatus();

			if (input instanceof DataSourceNode) {
				if (s != GraphNode.Status.success) {
					return true;
				}
			}
			else if (input instanceof EvaluationNode) {
				if (s != GraphNode.Status.success) {
					return true;
				}
			}
			else {
				// ModelNode or ModelMergeNode — at least one must succeed
				hasModelInputs = true;
				if (s == GraphNode.Status.success) {
					allModelInputsFailed = false;
				}
			}
		}

		return hasModelInputs && allModelInputsFailed;
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
