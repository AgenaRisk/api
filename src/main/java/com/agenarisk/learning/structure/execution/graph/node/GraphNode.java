package com.agenarisk.learning.structure.execution.graph.node;

import org.json.JSONObject;

/**
 * Minimal progress-only shim of the mainline api's {@code GraphNode}.
 *
 * <p>The parallel api fork does not (yet) carry the mainline workflow-graph execution framework
 * (GraphExecutionContext / WorkflowGraph / the full node hierarchy). The regression / logit
 * learning feature ported from mainline only depends on {@code GraphNode.emitProgress(...)} for
 * interim progress reporting from long-running loops (structure search, per-node fitting, etc.).
 * This shim provides just those two static methods, with signatures and behaviour identical to
 * mainline, so the ported learner/executor sources compile unchanged. If the full workflow-graph
 * framework is later synced into this fork, this shim is superseded by the real class.</p>
 */
public class GraphNode {

	public static void emitProgress(JSONObject event) {
		System.out.println(new JSONObject().put("_progress", event));
		System.out.flush();
	}

	/**
	 * Emits an interim (mid-execution) progress update for a long-running executor loop. Callers are
	 * responsible for their own throttling; this method does not rate-limit.
	 *
	 * @param nodeLabel the label of the node currently executing
	 * @param message human-readable status, e.g. "Evaluating row 1,200 of 20,000"
	 * @param current optional current step for determinate progress; null if unknown
	 * @param total optional total steps; null if unknown
	 */
	public static void emitProgress(String nodeLabel, String message, Integer current, Integer total) {
		JSONObject event = new JSONObject()
				.put("type", "nodeProgress")
				.put("nodeLabel", nodeLabel)
				.put("message", message);
		if (current != null){
			event.put("current", current);
		}
		if (total != null){
			event.put("total", total);
		}
		emitProgress(event);
	}
}
