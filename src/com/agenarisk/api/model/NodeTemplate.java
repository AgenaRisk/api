package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.util.JSONUtils;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import com.agenarisk.api.util.Ref;

/**
 *
 * @author Eugene Dementiev
 */
public class NodeTemplate {
	
	private static final JSONObject TEMPLATE = JSONUtils.toJSONObject(new Object[][]{
		{Ref.ID, "ID"},
		{Ref.NAME, "NAME"},
		{Ref.CONFIGURATION, JSONUtils.toJSONObject(new Object[][]{
			{Ref.TYPE, "NODE_TYPE"},
			{Ref.STATES, new JSONArray()},
			{Ref.TABLE, JSONUtils.toJSONObject(new Object[][]{
				{Ref.TYPE, "TABLE_TYPE"},
				{Ref.PROBABILITIES, new JSONArray()}
			})}
		})}
	});
	
	private JSONObject json;
	
	private NodeTemplate(Ref.NODE_TYPE type) {
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
	
	public static NodeTemplate createTemplate(Ref.NODE_TYPE type){
		return new NodeTemplate(type);
	}
	
	public JSONObject getJSON() {
		return json;
	}
	
	public NodeTemplate setId(String id) throws JSONException {
		json.put(Ref.ID, id);
		return this;
	}
	
	public NodeTemplate setName(String name) throws JSONException {
		json.put(Ref.NAME, name);
		return this;
	}

	public NodeTemplate setNodeType(Ref.NODE_TYPE nodeType) throws JSONException {
		json.getJSONObject(Ref.CONFIGURATION).put(Ref.TYPE, nodeType.toString());
		return this;
	}
	
	public NodeTemplate setStates(List<String> states) throws JSONException {
		json.getJSONObject(Ref.CONFIGURATION).put(Ref.STATES, new JSONArray(states));
		return this;
	}
	
	public NodeTemplate setStates(String[] states) throws JSONException {
		setStates(Arrays.asList(states));
		return this;
	}
	
	public NodeTemplate setTableType(Ref.TABLE_TYPE tableType) throws JSONException {
		json.getJSONObject(Ref.CONFIGURATION).getJSONObject(Ref.TABLE).put(Ref.TYPE, tableType.toString());
		return this;
	}
	
	public NodeTemplate setNPTRows(Double[][] rows) throws JSONException{
		/*
		{Ref.PROBABILITIES, new JSONArray(Arrays.asList(new JSONArray[]{
			new JSONArray(Arrays.asList(new Double[]{0.5})),
			new JSONArray(Arrays.asList(new Double[]{0.5}))
		}))}
		*/
		;
		json.getJSONObject(Ref.CONFIGURATION)
		.getJSONObject(Ref.TABLE)
		.put(Ref.PROBABILITIES, new JSONArray(
			Arrays.asList(rows).stream().map(darray -> new JSONArray(Arrays.asList(darray))).collect(Collectors.toList())
		));
		
		return this;
	}
	
	public NodeTemplate setNPTColumns(double[][] columns){
		return this;
	}
	
	public static JSONObject generateTableFromRows(Double[][] rows) throws NodeException{
		JSONObject jsonTable;
		NodeTemplate nt = createTemplate(Ref.NODE_TYPE.Boolean);
		try {
			nt.setNPTRows(rows);
			nt.setTableType(Ref.TABLE_TYPE.Manual);
			jsonTable = nt.getJSON().getJSONObject(Ref.CONFIGURATION).getJSONObject(Ref.TABLE);
		}
		catch (JSONException ex){
			throw new NodeException("Failed to generate a table JSON", ex);
		}
		
		return jsonTable;
	}
	
	public static void main(String[] args) throws Exception {
		
		JSONObject table = generateTableFromRows(new Double[][]{
			{0.1, 0.7},
			{0.9, 0.3}
		});
		
		System.out.println(table.toString(10));
		
		System.exit(0);
		
		NodeTemplate nt = createTemplate(Ref.NODE_TYPE.Boolean);
		
		nt.setId("n1n1");
		nt.setName("N1N1");
		nt.setNodeType(Ref.NODE_TYPE.Boolean);
		nt.setStates(new String[]{"False", "True"});
		nt.setTableType(Ref.TABLE_TYPE.Manual);
		nt.setNPTRows(new Double[][]{
			{0.1, 0.7},
			{0.9, 0.3}
		});
		
		System.out.println(nt.getJSON().toString(20));
	}
}
