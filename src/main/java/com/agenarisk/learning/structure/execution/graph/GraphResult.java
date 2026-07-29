package com.agenarisk.learning.structure.execution.graph;

import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class GraphResult {

	private final String label;
	private final String description;
	private final String version;
	private final List<GraphNode> nodes;

	public GraphResult(String label, String description, String version, List<GraphNode> nodes) {
		this.label = label != null ? label : "";
		this.description = description != null ? description : "";
		this.version = version != null ? version : "";
		this.nodes = nodes;
	}

	public JSONObject toJson() {
		JSONObject json = new JSONObject();
		json.put("label", label);
		json.put("description", description);
		json.put("version", version);
		JSONArray nodesArray = new JSONArray();
		for (GraphNode node : nodes) {
			nodesArray.put(node.toJson());
		}
		json.put("nodes", nodesArray);
		return json;
	}

	public List<List<Object>> getSummary() {
		List<List<Object>> rows = new ArrayList<>();
		for (GraphNode node : nodes) {
			List<Object> row = new ArrayList<>();
			row.add(node.getLabel());
			row.add(node.getType());
			row.add(node.getSubType());
			row.add(node.getStatus().name());
			row.add(node.getStatusMessage());
			rows.add(row);
		}
		return rows;
	}
}
