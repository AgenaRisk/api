package com.agenarisk.api.model;

import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.io.stub.NodeConfiguration;
import com.agenarisk.api.util.JSONUtils;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class NodeTemplate {
	
	private static final JSONObject TEMPLATE = JSONUtils.toJSONObject(new Object[][]{
		{Node.Field.id, "ID"},
		{Node.Field.name, "NAME"},
		{NodeConfiguration.Field.configuration, JSONUtils.toJSONObject(new Object[][]{
			{NodeConfiguration.Field.type, "NODE_TYPE"},
			{State.Field.states, new JSONArray()},
			{NodeConfiguration.Table.table, JSONUtils.toJSONObject(new Object[][]{
				{NodeConfiguration.Table.type, "TABLE_TYPE"},
				{NodeConfiguration.Table.probabilities, new JSONArray()}
			})}
		})}
	});
	
	private JSONObject json;
	
	private NodeTemplate(Node.Type type) {
		try {
			json = new JSONObject(TEMPLATE.toString());
			setNodeType(type);
			
			switch(type){
				case Boolean:
					setStates(new String[]{"False", "True"});
					break;
				
				default:
			}
		}
		catch (JSONException ex){
			
		}
	}
	
	public static NodeTemplate createTemplate(Node.Type type){
		return new NodeTemplate(type);
	}
	
	public JSONObject getJSON() {
		return json;
	}
	
	public NodeTemplate setId(String id) throws JSONException {
		json.put(Node.Field.id.toString(), id);
		return this;
	}
	
	public NodeTemplate setName(String name) throws JSONException {
		json.put(Node.Field.name.toString(), name);
		return this;
	}

	public NodeTemplate setNodeType(Node.Type nodeType) throws JSONException {
		json.getJSONObject(NodeConfiguration.Field.configuration.toString()).put(NodeConfiguration.Field.type.toString(), nodeType);
		return this;
	}
	
	public NodeTemplate setStates(List<String> states) throws JSONException {
		json.getJSONObject(NodeConfiguration.Field.configuration.toString()).put(State.Field.states.toString(), new JSONArray(states));
		return this;
	}
	
	public NodeTemplate setStates(String[] states) throws JSONException {
		setStates(Arrays.asList(states));
		return this;
	}
	
	public NodeTemplate setTableType(NodeConfiguration.TableType tableType) throws JSONException {
		json.getJSONObject(NodeConfiguration.Field.configuration.toString()).getJSONObject(NodeConfiguration.Table.table.toString()).put(NodeConfiguration.Table.type.toString(), tableType);
		return this;
	}
	
	public NodeTemplate setNPTRows(Double[][] rows) throws JSONException{
		/*
		{NodeConfiguration.Table.probabilities, new JSONArray(Arrays.asList(new JSONArray[]{
			new JSONArray(Arrays.asList(new Double[]{0.5})),
			new JSONArray(Arrays.asList(new Double[]{0.5}))
		}))}
		*/
		;
		json.getJSONObject(NodeConfiguration.Field.configuration.toString())
		.getJSONObject(NodeConfiguration.Table.table.toString())
		.put(NodeConfiguration.Table.probabilities.toString(), new JSONArray(
			Arrays.asList(rows).stream().map(darray -> new JSONArray(Arrays.asList(darray))).collect(Collectors.toList())
		));
		
		return this;
	}
	
	public NodeTemplate setNPTColumns(double[][] columns){
		return this;
	}
	
	public static JSONObject generateTableFromRows(Double[][] rows) throws NodeException{
		JSONObject jsonTable;
		NodeTemplate nt = createTemplate(Node.Type.Boolean);
		try {
			nt.setNPTRows(rows);
			nt.setTableType(NodeConfiguration.TableType.Manual);
			jsonTable = nt.getJSON().getJSONObject(NodeConfiguration.Field.configuration.toString()).getJSONObject(NodeConfiguration.Table.table.toString());
		}
		catch (JSONException ex){
			throw new NodeException("Failed to generate a table JSON", ex);
		}
		
		return jsonTable;
	}
	
	/*
	public static void main(String[] args) throws Exception {
		
		JSONObject table = generateTableFromRows(new Double[][]{
			{0.1, 0.7},
			{0.9, 0.3}
		});
		
		System.out.println(table.toString(10));
		
		System.exit(0);
		
		NodeTemplate nt = createTemplate(Node.Type.Boolean);
		
		nt.setId("n1n1");
		nt.setName("N1N1");
		nt.setNodeType(Node.Type.Boolean);
		nt.setStates(new String[]{"False", "True"});
		nt.setTableType(NodeConfiguration.TableType.Manual);
		nt.setNPTRows(new Double[][]{
			{0.1, 0.7},
			{0.9, 0.3}
		});
		
		System.out.println(nt.getJSON().toString(20));
	}
	*/
}
