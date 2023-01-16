package com.agenarisk.api.io;

import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.io.stub.Audit;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.model.NodeConfiguration;
import com.agenarisk.api.io.stub.RiskTable;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Link;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.Observation;
import com.agenarisk.api.model.State;
import com.agenarisk.api.model.ResultValue;
import com.agenarisk.api.util.JSONUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

/**
 * Converts AgenaRisk Model JSON structure into an equivalent AgenaRisk Model XML structure and vice-versa.
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
		WRAPPER_MAP.put(Graphics.CanvasData.canvasData.toString(), Graphics.CanvasData.canvas.toString());
		
		WRAPPER_MAP.put(Network.Field.description.toString(), "CDATA");
		WRAPPER_MAP.put(Node.Field.description.toString(), "CDATA");
		WRAPPER_MAP.put(NodeConfiguration.Table.expression.toString(), "CDATA");
		WRAPPER_MAP.put(Meta.Field.text.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.Field.viewSettings.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.Field.objectDefaults.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.Field.openMonitors.toString(), "CDATA");
		WRAPPER_MAP.put(Graphics.CanvasData.canvas.toString(), "CDATA");
		WRAPPER_MAP.put(Audit.Field.comment.toString(), "CDATA");
//		WRAPPER_MAP.put(RiskTable.Question.name.toString(), "CDATA");
		
		WRAPPER_MAP.put(NodeConfiguration.Table.probabilities.toString(), "row");
		WRAPPER_MAP.put("row", "cell");
	}

	/**
	 * Parses XML string to JSONObject and then formats the JSONObject to conform to AgenaRisk 10 JSON model format.
	 * 
	 * @param xmlString XML string to parse
	 * 
	 * @return corresponding JSONObject
	 * 
	 * @throws AdapterException if invalid XML structure or unexpected values
	 */
	public static JSONObject xmlToJson(String xmlString) throws AdapterException {
		JSONObject json;
		try {
			json = XML.toJSONObject(xmlString);
			convertXmlJson(json);
		}
		catch (JSONException ex){
			throw new AdapterException("Failed to convert model XML to JSON", ex);
		}
		
		return json;
	}
	
	/**
	 * Converts a JSON AgenaRisk 10 model to its XML representation.
	 * 
	 * @param o object in JSON format (JSONObject, JSONArray, JsonObject, JsonArray, etc)
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
		StringBuilder sb = new StringBuilder();
		
		String prefix = "";
		String suffix = "";
		
		if (o instanceof String && prefix.isEmpty() && suffix.isEmpty()){
			wrapper = "CDATA";
		}
		
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
		
		if (o instanceof JSONObject){
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

	/**
	 * Traverses the JSON tree and removes unnecessary wrappers that were introduced in the original JSON to XML conversion, e.g. turning
	 * <pre>
	 * &lt;networks&gt;
	 *   &lt;network&gt;net1&lt;/network&gt;
	 *   &lt;network&gt;net2&lt;/network&gt;
	 * &lt;/networks&gt;
	 * </pre>
	 * into
	 * <pre>
	 * {"networks":[
	 *   "net1",
	 *   "net2"
	 * ]}
	 * </pre>
	 * Will also convert
	 * <pre>
	 * &lt;probabilities&gt;
	 * 	&lt;row&gt;
	 * 		&lt;cell&gt;1&lt;/cell&gt;
	 * 		&lt;cell&gt;2&lt;/cell&gt;
	 * 	&lt;/row&gt;
	 * 	&lt;row&gt;
	 * 		&lt;cell&gt;3&lt;/cell&gt;
	 * 		&lt;cell&gt;4&lt;/cell&gt;
	 * 	&lt;/row&gt;
	 * &lt;/probabilities&gt;
	 * </pre>
	 * into
	 * <pre>
	 * "probabilities": [[1, 2], [3, 4]]
	 * </pre>
	 * 
	 * @param o object to process
	 * 
	 * @throws JSONException if JSON is malformed or does not conform to the expected format extracted from XML
	 */
	private static void convertXmlJson(Object o) throws JSONException, AdapterException{
		if (o instanceof JSONObject){
			JSONObject jo = (JSONObject) o;
			
			Iterator<String> keys = jo.keys();
			while(keys.hasNext()){
				String key = keys.next();
				
				String wrapper = WRAPPER_MAP.get(key);
				
				if (NodeConfiguration.Table.probabilities.toString().equals(key)){
					
					boolean columns = false;
					boolean rows = false;
					
					JSONObject jTable = jo.optJSONObject(NodeConfiguration.Table.probabilities.toString());
					try {
						columns = jTable.has(NodeConfiguration.Table.column.toString());
						rows = jTable.has(NodeConfiguration.Table.row.toString());
					}
					catch (NullPointerException ex){
						// Probably no probabilities in the table
					}
					
					if (rows && columns){
						throw new AdapterException("Node table can contain either rows or columns, but not both");
					}
					
					String groupKey = "";
					if (columns){
						groupKey = NodeConfiguration.Table.column.toString();
						// JSON format assumes probabilities are rows, so indicate otherwise
						jo.put(NodeConfiguration.Table.pvalues.toString(), groupKey);
					}
					else {
						groupKey = NodeConfiguration.Table.row.toString();
					}
					
					if (rows || columns){
						JSONUtils.convertToJSONArray(jo, key, groupKey);
						JSONUtils.convertTo2DArray(jo, key, NodeConfiguration.Table.cell.toString());
					}
				}
				else if (wrapper != null && !wrapper.equals("CDATA")){
					JSONUtils.convertToJSONArray(jo, key, wrapper);
				}
				
				convertXmlJson(jo.opt(key));
			}
		}
		else if (o instanceof JSONArray){
			JSONArray ja = (JSONArray) o;
			for (int i = 0; i < ja.length(); i++) {
				convertXmlJson(ja.opt(i));
			}
		}
		
		// Don't do anything special for other types
	}
	
}
