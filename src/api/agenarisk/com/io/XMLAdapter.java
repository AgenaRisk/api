package api.agenarisk.com.io;

import com.agenarisk.api.util.JSONUtils;
import com.agenarisk.api.util.Ref;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.xml.XML;

/**
 *
 * @author Eugene Dementiev
 */
public class XMLAdapter {

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
	 * <li>node.definition.states.state</li>
	 * <li>node.definition.table.expressions.expression</li>
	 * <li>node.definition.table.partitions.partition</li>
	 * <li>node.definition.table.probabilities.row</li>
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
	
}
