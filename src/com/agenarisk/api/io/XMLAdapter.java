package com.agenarisk.api.io;

import com.agenarisk.api.io.stub.Audit;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.NodeConfiguration;
import com.agenarisk.api.io.stub.RiskTable;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Link;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.Observation;
import com.agenarisk.api.model.State;
import com.agenarisk.api.model.dataset.ResultValue;
import com.agenarisk.api.util.JSONUtils;
import com.agenarisk.api.util.Ref;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.xml.XML;

/**
 *
 * @author Eugene Dementiev
 */
public class XMLAdapter {
	
	private static final Map<String, String> WRAPPER_MAP = new HashMap<>();
	
	static {
		WRAPPER_MAP.put(Network.Field.networks.toString(), Network.Field.network.toString());
		WRAPPER_MAP.put(Network.ModificationLog.modificationLog.toString(), Network.ModificationLog.entry.toString());
		WRAPPER_MAP.put(Node.Field.nodes.toString(), Node.Field.node.toString());
		WRAPPER_MAP.put(State.Field.states.toString(), State.Field.state.toString());
		WRAPPER_MAP.put(NodeConfiguration.Table.expressions.toString(), NodeConfiguration.Table.expression.toString());
		WRAPPER_MAP.put(NodeConfiguration.Table.partitions.toString(), NodeConfiguration.Table.partition.toString());
		WRAPPER_MAP.put(NodeConfiguration.Variables.variables.toString(), NodeConfiguration.Variables.variable.toString());
		WRAPPER_MAP.put(Link.Field.links.toString(), Link.Field.link.toString());
		WRAPPER_MAP.put(DataSet.Field.dataSets.toString(), DataSet.Field.dataSet.toString());
		WRAPPER_MAP.put(CalculationResult.Field.results.toString(), CalculationResult.Field.result.toString());
		WRAPPER_MAP.put(ResultValue.Field.resultValues.toString(), ResultValue.Field.resultValue.toString());
		WRAPPER_MAP.put(Observation.Field.entries.toString(), Observation.Field.entry.toString());
		WRAPPER_MAP.put(Observation.Field.observations.toString(), Observation.Field.observation.toString());
		WRAPPER_MAP.put(RiskTable.Field.riskTable.toString(), RiskTable.Field.questionnaire.toString());
		WRAPPER_MAP.put(RiskTable.Question.questions.toString(), RiskTable.Question.question.toString());
		WRAPPER_MAP.put(RiskTable.Answer.answers.toString(), RiskTable.Answer.answer.toString());
		WRAPPER_MAP.put(Meta.Field.notes.toString(), Meta.Field.note.toString());
		WRAPPER_MAP.put(Audit.Field.changelog.toString(), Audit.Field.change.toString());
		
		WRAPPER_MAP.put(Network.Field.description.toString(), "CDATA");
		WRAPPER_MAP.put(Node.Field.description.toString(), "CDATA");
		WRAPPER_MAP.put(NodeConfiguration.Table.expression.toString(), "CDATA");
		WRAPPER_MAP.put(Meta.Field.text.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.Field.viewSettings.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.Field.objectDefaults.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.Field.openMonitors.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.CanvasData.canvasData.toString(), "CDATA");
		WRAPPER_MAP.put(Audit.Field.comment.toString(), "CDATA");
		
		WRAPPER_MAP.put(NodeConfiguration.Table.probabilities.toString(), "row");
		WRAPPER_MAP.put("row", "cell");
	}

	/**
	 * Reads XML from String and converts it to a corresponding JSONObject.
	 * <br>
	 * Note that expected arrays are parsed by converting a parent tag containing series of siblings into a JSONArray omitting the immediate child tag, e.g.
	 * <br>
	 * &lt;parent&gt;
	 * <br>
	 * &nbsp;&lt;child&gt;A&lt;/child&gt;
	 * <br>
	 * &nbsp;&lt;child&gt;b&lt;/child&gt;
	 * <br>
	 * &lt;/parent&gt;
	 * <br>
	 * will be converted into {"parent":["A","B"]}
	 * <br>
	 * This conversion applies to:
	 * <br>
	 * <ul>
	 * <li>model.links.link</li>
	 * <li>model.networks.network</li>
	 * <li>network.nodes.node</li>
	 * <li>network.links.link</li>
	 * <li>node.meta.notes.note</li>
	 * <li>node.configuration.states.state</li>
	 * <li>node.configuration.table.expressions.expression</li>
	 * <li>node.configuration.table.partitions.partition</li>
	 * <li>node.configuration.table.probabilities.row</li>
	 * <li>row.cell</li>
	 * </ul>
	 * <br>
	 * Other unexpected array structures will be converted by grouping siblings into an JSONArray, e.g.
	 * <br>
	 * {"parent":{"child":["A","B"]}}
	 * 
	 * @param xmlString
	 * 
	 * @return corresponding JSONObject
	 * @throws JSONException if invalid XML structure or unexpected values
	 */
	public static JSONObject parseXMLAsJSON(String xmlString) throws JSONException {
		JSONObject json = XML.toJSONObject(xmlString);
		
		JSONUtils.convertToJSONArray(json.getJSONObject(Ref.MODEL), Ref.NETWORKS, Ref.NETWORK);
		JSONUtils.convertToJSONArray(json.getJSONObject(Ref.MODEL), Ref.LINKS, Ref.LINK);

		JSONArray jsonModelNetworks = json.getJSONObject(Ref.MODEL).getJSONArray(Ref.NETWORKS);
		for(int n = 0; n < jsonModelNetworks.length(); n++){
			JSONObject jsonNetwork = jsonModelNetworks.getJSONObject(n);
			JSONUtils.convertToJSONArray(jsonNetwork, Ref.NODES, Ref.NODE);
			JSONUtils.convertToJSONArray(jsonNetwork, Ref.LINKS, Ref.LINK);
			
			JSONArray jsonNodes = jsonNetwork.getJSONArray(Ref.NODES);
			for(int i = 0; i < jsonNodes.length(); i++){
				JSONObject jsonNode = jsonNodes.getJSONObject(i);
				JSONObject jsonNodeDefinition = jsonNode.getJSONObject(Ref.CONFIGURATION);
				if (jsonNodeDefinition.has(Ref.STATES)){
					JSONUtils.convertToJSONArray(jsonNodeDefinition, Ref.STATES, Ref.STATE);
				}
				
				if (!jsonNodeDefinition.has(Ref.TABLE)){
					jsonNodeDefinition.put(Ref.TABLE, new JSONObject());
					continue;
				}
				
				JSONObject jsonTable = jsonNodeDefinition.getJSONObject(Ref.TABLE);
				if (jsonTable.has(Ref.EXPRESSIONS)){
					JSONUtils.convertToJSONArray(jsonTable, Ref.EXPRESSIONS, Ref.EXPRESSION);
				}
				if (jsonTable.has(Ref.PARTITIONS)){
					JSONUtils.convertToJSONArray(jsonTable, Ref.PARTITIONS, Ref.PARTITION);
				}
				if (jsonTable.has(Ref.PROBABILITIES)){
					JSONUtils.convertToJSONArray(jsonTable, Ref.PROBABILITIES, Ref.ROW);
					JSONUtils.convertTo2DArray(jsonTable, Ref.PROBABILITIES, Ref.CELL);
				}
			}
		}
		
		return json;
	}
	
	/**
	 * Converts an object to its XML representation.
	 * <br>
	 * Intended for converting JSON AgenaRisk 10 model to XML format.
	 * 
	 * @param o object in JSON format
	 * 
	 * @return object in XML format
	 */
	public static String toXMLString(Object o){
		return toXMLString(o, null);
	}
	
	/**
	 * Converts an object to its XML representation.
	 * <br>
	 * Intended for converting JSON AgenaRisk 10 model to XML format.
	 * <br>
	 * If o is JSONObject, will wrap the object representation into an open-close wrapper tag pair, and will use keys to resolve wrappers for its children.
	 * <br>
	 * If o is a JSONArray, will wrap each element into an open-close wrapper tag pair, and will use the wrapper to resolve wrapper for its elements.
	 * <br>
	 * Otherwise will wrap the element into an open-close wrapper tag pair.
	 * <br>
	 * If wrapper is CDATA then will wrap into XML CDATA.
	 * <br>
	 * Wrapper can be comma-delimited, in which case will wrap into a corresponding sequence of tags (sequence reverted for closing).
	 * 
	 * @param o object in JSON format
	 * @param wrapper additional wrapper to use when converting
	 * 
	 * @return object in XML format
	 */
	public static String toXMLString(Object o, String wrapper){
		
		System.out.println(o.getClass() + " -> "+wrapper);
		StringBuilder sb = new StringBuilder();
		
		String prefix = "";
		String suffix = "";
		
		if (wrapper != null){
			if (wrapper.equals("CDATA")){
				prefix = "<![CDATA[";
				suffix = "]]>";
			}
			else {
				List<String> wrappers = Arrays.asList(wrapper.split(","));
				prefix = wrappers.stream().map(w -> "<"+w+">").collect(Collectors.joining(""));
				Collections.reverse(wrappers);
				suffix = wrappers.stream().map(w -> "</"+w+">").collect(Collectors.joining(""));
			}
		}
		if (o instanceof JsonObject){
			if (!prefix.isEmpty()){
				sb.append(prefix);
			}
			
			JsonObject jo = (JsonObject) o;
			for(String key: jo.keySet()){
				sb.append("<").append(key).append(">");
				
				String wrapperNext = WRAPPER_MAP.get(key);
				
				sb.append(toXMLString(jo.get(key), wrapperNext));
				sb.append("</").append(key).append(">");
			}
			
			if (!suffix.isEmpty()){
				sb.append(suffix);
			}
		}
		else if (o instanceof JsonArray){
			JsonArray ja = (JsonArray)o;
			for (int i = 0; i < ja.size(); i++) {
				if (!prefix.isEmpty()){
					sb.append(prefix);
				}
				
				String wrapperNext = WRAPPER_MAP.get(wrapper);
				
				sb.append(toXMLString(ja.get(i), wrapperNext));
				
				if (!suffix.isEmpty()){
					sb.append(suffix);
				}
			}
		}
		else if (o instanceof JSONObject){
			if (!prefix.isEmpty()){
				sb.append(prefix);
			}
			
			JSONObject jo = (JSONObject) o;
			Iterator<String> keys = jo.keys();
			while(keys.hasNext()){
				String key = keys.next();
				sb.append("<").append(key).append(">");
				
				String wrapperNext = WRAPPER_MAP.get(key);
				
				sb.append(toXMLString(jo.opt(key), wrapperNext));
				sb.append("</").append(key).append(">");
			}
			
			if (!suffix.isEmpty()){
				sb.append(suffix);
			}
		}
		else if (o instanceof JSONArray){
			JSONArray ja = (JSONArray)o;
			for (int i = 0; i < ja.length(); i++) {
				if (!prefix.isEmpty()){
					sb.append(prefix);
				}
				
				String wrapperNext = WRAPPER_MAP.get(wrapper);
				
				sb.append(toXMLString(ja.opt(i), wrapperNext));
				
				if (!suffix.isEmpty()){
					sb.append(suffix);
				}
			}
		}
		else {
			
			if (!prefix.isEmpty()){
				sb.append(prefix);
			}
			
			sb.append(o);
			
			if (!suffix.isEmpty()){
				sb.append(suffix);
			}
		}
		
		return sb.toString();
	}
	
}
