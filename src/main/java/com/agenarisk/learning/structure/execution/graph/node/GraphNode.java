package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import org.json.JSONObject;
import uk.co.agena.minerva.util.io.MinervaProperties;

public abstract class GraphNode {

	public static final String PROP_DEBUG = "com.agenarisk.learning.structure.debug";

	public enum Status {
		pending, success, failure, skipped
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
}
