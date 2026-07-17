package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;
import uk.co.agena.minerva.util.io.MinervaProperties;

public abstract class GraphNode {

	public static final String PROP_DEBUG = "com.agenarisk.learning.structure.debug";

	public enum Status {
		pending, success, warning, failure
	}

	private String label;
	private String description = "";
	private boolean useCache = false;
	private Status status = Status.pending;
	private String statusMessage = "";
	private String debugInfo = "";
	private Object resultData = null;

	public abstract Set<String> getInputLabels();

	public abstract void parseOptions(JSONObject jOptions);

	public abstract void execute(GraphExecutionContext ctx);

	public abstract String getSubType();

	public String getType() {
		if (this instanceof ModelNode) {
			return "model";
		}
		if (this instanceof EvaluationNode) {
			return "evaluation";
		}
		if (this instanceof DataSourceNode) {
			return "dataSource";
		}
		return "modelMerge";
	}

	public void parseCommon(JSONObject jNode) {
		this.label = jNode.getString("label");
		this.description = jNode.optString("description", "");
		this.useCache = jNode.optBoolean("useCache", false);
		parseNodeFields(jNode);
		JSONObject jOptions = jNode.optJSONObject("options");
		parseOptions(jOptions != null ? jOptions : new JSONObject());
	}

	protected void parseNodeFields(JSONObject jNode) {
		// hook for subclasses to parse additional root-level fields
	}

	public static boolean isDebugEnabled() {
		return Boolean.parseBoolean(MinervaProperties.getProperty(PROP_DEBUG, "false"));
	}

	public static String stackTraceOf(Throwable ex) {
		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

	public boolean isUseCache() {
		return useCache;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatusMessage(String msg) {
		this.statusMessage = msg != null ? msg : "";
	}

	public String getDebugInfo() {
		return debugInfo;
	}

	public void setDebugInfo(String info) {
		this.debugInfo = info != null ? info : "";
	}

	public Object getResultData() {
		return resultData;
	}

	public void setResult(JSONObject result) {
		this.resultData = result;
	}

	public void setResult(org.json.JSONArray result) {
		this.resultData = result;
	}

	public void failWith(String message, Throwable ex) {
		setStatus(Status.failure);
		setStatusMessage(message);
		if (isDebugEnabled() && ex != null) {
			setDebugInfo(stackTraceOf(ex));
		}
	}

	/**
	 * Resolve a linked data source, failing with a clear, actionable message
	 * rather than a NullPointerException when the input is missing, unknown, or
	 * of the wrong type. Intended to be called at the top of {@link #execute}.
	 */
	protected DataSourceNode requireDataSource(GraphExecutionContext ctx, String label) {
		if (label == null || label.isEmpty()) {
			throw new StructureLearningException(
					"'" + getLabel() + "' has no data source connected. Link a data source (CSV) node to its input.");
		}
		GraphNode input = ctx.getNode(label);
		if (input == null) {
			throw new StructureLearningException(
					"'" + getLabel() + "' references data source '" + label + "', which does not exist in the workflow.");
		}
		if (!(input instanceof DataSourceNode)) {
			throw new StructureLearningException(
					"'" + getLabel() + "' expects a data source input, but '" + label + "' is a " + input.getSubType() + " node.");
		}
		return (DataSourceNode) input;
	}

	/**
	 * Validate that a linked model input is present, known and of model type.
	 * The model artefact itself is loaded by the caller from the output dir.
	 */
	protected void requireModelInput(GraphExecutionContext ctx, String label) {
		if (label == null || label.isEmpty()) {
			throw new StructureLearningException(
					"'" + getLabel() + "' has no model connected. Link a model-producing node to its input.");
		}
		GraphNode input = ctx.getNode(label);
		if (input == null) {
			throw new StructureLearningException(
					"'" + getLabel() + "' references model '" + label + "', which does not exist in the workflow.");
		}
		if (!(input instanceof ModelNode)) {
			throw new StructureLearningException(
					"'" + getLabel() + "' expects a model input, but '" + label + "' is a " + input.getSubType() + " node.");
		}
	}

	/**
	 * Resolve a linked evaluation input, failing with a clear message rather
	 * than a NullPointerException when it is missing or of the wrong type.
	 */
	protected EvaluationNode requireEvaluation(GraphExecutionContext ctx, String label) {
		if (label == null || label.isEmpty()) {
			throw new StructureLearningException(
					"'" + getLabel() + "' has no evaluation connected. Link an evaluation node to its input.");
		}
		GraphNode input = ctx.getNode(label);
		if (input == null) {
			throw new StructureLearningException(
					"'" + getLabel() + "' references evaluation '" + label + "', which does not exist in the workflow.");
		}
		if (!(input instanceof EvaluationNode)) {
			throw new StructureLearningException(
					"'" + getLabel() + "' expects an evaluation input, but '" + label + "' is a " + input.getSubType() + " node.");
		}
		return (EvaluationNode) input;
	}

	public static void emitProgress(JSONObject event) {
		System.out.println(new JSONObject().put("_progress", event));
		System.out.flush();
	}

	/**
	 * Emits an interim (mid-execution) progress update for a node whose own {@code execute()} is still running -
	 * unlike {@code nodeStart}/{@code nodeComplete} (emitted exactly once each, by {@code GraphExecutor}), this can
	 * be called any number of times by the executor code a node delegates to, for long-running loops (structure
	 * search iterations, per-row evaluation, per-node fitting, EM iterations) that would otherwise leave the UI
	 * showing a static "computing" spinner for minutes with no feedback.
	 * <br>
	 * Callers are responsible for their own throttling (e.g. only every Nth row/iteration) - this method itself
	 * does not rate-limit.
	 *
	 * @param nodeLabel the label of the node currently executing
	 * @param message human-readable status, e.g. "Evaluating row 1,200 of 20,000"
	 * @param current optional current step, for determinate progress (e.g. current row/iteration); null if unknown
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

	public static String friendlyMessage(Exception ex) {
		if (ex instanceof StructureLearningException || ex instanceof IllegalArgumentException) {
			return ex.getMessage();
		}
		String msg = ex.getMessage();
		if (msg == null || msg.startsWith("Cannot invoke") || msg.startsWith("Cannot read field") || msg.startsWith("Cannot store")) {
			return isDebugEnabled() ? msg : "Unexpected error (enable debug mode for details)";
		}
		return msg;
	}

	public JSONObject toJson() {
		JSONObject json = new JSONObject();
		json.put("label", label);
		json.put("type", getType());
		json.put("subType", getSubType());
		json.put("status", status.name());
		if (!statusMessage.isEmpty()) {
			json.put("statusMessage", statusMessage);
		}
		if (isDebugEnabled() && !debugInfo.isEmpty()) {
			json.put("debug", debugInfo);
		}
		if (resultData != null) {
			json.put("result", resultData);
		}
		return json;
	}

	public List<Path> getOutputFiles(GraphExecutionContext ctx) {
		return Collections.emptyList();
	}
}
