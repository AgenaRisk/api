package com.agenarisk.api.util;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.ModelException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.util.io.FileHandler;
import uk.co.agena.minerva.util.io.FileHandlingException;

/**
 * Provides convenience functionality for working with JSONObject and JSONArray classes.
 * 
 * @author Eugene Dementiev
 */
public class JSONUtils {
	
	/**
	 * Converts a 2D Object array into a JSONObject.
	 * <br>
	 * The first dimension represents a list of map entries.
	 * <br>
	 * The second dimension represents a map entry, where first element is its String key, and the second is the value.
	 * 
	 * @param array Basic 2D Object array representation of the map
	 * 
	 * @return JSONObject representation of the given array
	 */
	public static JSONObject toJSONObject(Object[][] array){
		return new JSONObject(Arrays.stream(array).collect(Collectors.toMap(kv -> kv[0].toString(), kv -> kv[1])));
	}
	
	/**
	 * This is a helper function to create a message for a JSONException that is formatted as suitable for display to a user.
	 * 
	 * @param ex Exception that has the message to convert
	 * 
	 * @return user-friendly error message
	 */
	public static String createMissingAttrMessage(JSONException ex){
		if (ex.getMessage() == null){
			return "JSON missing a required attribute";
		}
		
		if (ex.getMessage().contains("JSONObject[")){
			return "JSON missing required attribute: " + ex.getMessage().replaceFirst("JSONObject\\[\"(.*)\"\\] not found.", "$1");
		}

		return "JSON missing required attribute at index: " + ex.getMessage().replaceFirst("JSONArray\\[(.*)\\] not found.", "$1");
	}
	
	/**
	 * Converts JSONArray to a List of provided type.
	 * 
	 * @param <T> type of objects expected to be in the list
	 * @param jarray JSONArray array
	 * @param classType Class of items to be in the list
	 * 
	 * @return List containing items from the JSONArray
	 */
	public static <T> List<T> toList(JSONArray jarray, Class<T> classType) {
		List<T> list = new ArrayList<>();
		for(int i = 0; i < jarray.length(); i++){
			T item = (T) jarray.opt(i);
			if (item != null){
				list.add(item);
			}
		}
		return list;
	}
	
	/**
	 * Converts an array of mixed objects into a 2D JSONArray.
	 * <br>
	 * The array resides under the `parentKey` in grandParent JSONObject.
	 * <br>
	 * Elements of the original array are JSONObjects and contain an item of key `elementKey`. This item could in turn be a JSONArray or a simple type.
	 * <br>
	 * After the operation, the original JSONArray will contain JSONArrays, such that the `elementKey` level is omitted.
	 * <br>
	 * For example,
	 * <br>
	 * {"table":{"probs":[{"cell":0.2},{"cell":0.8}]}}
	 * <br>
	 * will be converted to
	 * <br>
	 * {"table":{"probs":[[0.2],[0.8]]}}
	 * <br>
	 * And
	 * <br>
	 * {"table":{"probs":[{"cell":[0.2,0.7},{"cell":[0.8,0.3]}]}}
	 * <br>
	 * will be converted to
	 * <br>
	 * {"table":{"probs":[[0.2, 0.7],[0.8, 0.3]]}}
	 * 
	 * @param grandParent JSONObject that should contain the 2D array (in the example - object `table`)
	 * @param parentKey the key of the 2D array in grandParent (in the example - "probs")
	 * @param elementKey the elementKey to be omitted (in the example - "cell")
	 * 
	 * @throws JSONException 
	 */
	public static void convertTo2DArray(JSONObject grandParent, String parentKey, String elementKey) throws JSONException{
		// E.g. table (obj), probabilities(arr), cell(string/arr)
		JSONArray arrayOld = grandParent.getJSONArray(parentKey);
		
		for (int r = 0; r < arrayOld.length(); r++){
			
			// E.g. JSONObject that has key "cell"
			JSONObject itemLevel1 = arrayOld.getJSONObject(r);
			Object itemLevel2 = itemLevel1.get(elementKey);
			
			if (!(itemLevel2 instanceof JSONArray)){
				// Convert array element to array
				// E.g. "cell" only has a single item (string of double)
				JSONArray itemLevel1New = new JSONArray();
				itemLevel1New.put(itemLevel2);
				arrayOld.put(r, itemLevel1New);
			}
			else {
				// Otherwise just omit elementKey and replace the object with the key by this array
				arrayOld.put(r, itemLevel2);
			}
			
		}
	}
	
	/**
	 * Omits the key `childKey` and puts its contents directly under the `parentKey` as a JSONArray.
	 * <br>
	 * Wraps into JSONArray as necessary.
	 * <br>
	 * For example,
	 * <br>
	 * {"network":{"nodes":{"node":"X"}}}
	 * <br>
	 * will be converted to
	 * <br>
	 * {"network":{"nodes":["X"]}
	 * <br>
	 * And
	 * <br>
	 * {"network":{"nodes":{"node":["X","Y"]}}}
	 * <br>
	 * will be converted to
	 * <br>
	 * {"network":{"nodes":["X", "Y"]}
	 * 
	 * @param grandParent JSONObject containing `parentKey`, in the example - object network
	 * @param parentKey - the key under which to put the resulting array, in the example - "nodes"
	 * @param childKey - the key to omit (and wrap contents into array if necessary), in the example - "node"
	 * 
	 * @throws JSONException 
	 */
	public static void convertToJSONArray(JSONObject grandParent, String parentKey, String childKey) throws JSONException {
		
		JSONArray jsonArray = new JSONArray();
		
		if (!grandParent.has(parentKey)){
			grandParent.put(parentKey, new JSONArray());
			return;
		}
		
		Object jsonParentObject = grandParent.get(parentKey);
		Object jsonChildObject;
		if (jsonParentObject instanceof JSONObject){
			jsonChildObject = ((JSONObject)jsonParentObject).get(childKey);
		}
		else if (jsonParentObject instanceof JSONArray){
			jsonChildObject = ((JSONArray)jsonParentObject).getJSONObject(0).get(childKey);
		}
		else if (jsonParentObject instanceof String){
			grandParent.put(parentKey, new JSONArray());
			return;
		}
		else {
			throw new JSONException("Wrong object type for `"+parentKey+"`.`"+childKey+"`");
		}
		
		if (jsonChildObject instanceof JSONArray){
			jsonArray = (JSONArray) jsonChildObject;
		}
		else {
			jsonArray.put(jsonChildObject);
		}
		
		grandParent.put(parentKey, jsonArray);
	}
	
	/**
	 * Wraps all elements of the provided JSONArray into JSONObjects that each contains the element under the provided key.
	 * 
	 * @param parent the array to wrap elements of
	 * @param wrapperName the key name under which to wrap each element
	 */
	public static void wrapArrayElements(JSONArray parent, String wrapperName){
		for(int i = 0; i < parent.length(); i++){
			Object o = parent.opt(i);
			JSONObject wrapper = new JSONObject();
			try {
				wrapper.put(wrapperName, o);
				parent.put(i, wrapper);
			}
			catch (JSONException ex){
				throw new AgenaRiskRuntimeException("Failed to wrap elements", ex);
			}
		}
	}
	
	/**
	 * Loads Model configuration from an XML file and converts it into suitable JSONObject.
	 * 
	 * @param path XML file path
	 * 
	 * @return Model configuration in valid JSONObject format
	 * @throws ModelException if failed to read from file
	 * @throws JSONException if XML structure is invalid or inconsistent
	 */
	public static JSONObject loadModelXML(String path) throws ModelException, JSONException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Loads Model configuration from a JSON file.
	 * 
	 * @param path JSON file path
	 * 
	 * @return Model configuration in JSONObject format
	 * @throws ModelException if failed to read from file
	 * @throws JSONException if JSON structure is invalid or inconsistent
	 */
	public static JSONObject loadModelJSON(String path) throws ModelException, JSONException {
		String jsonString;
		JSONObject json;
		path = path.replaceFirst("^[\"'](.*)[\"']$", "$1");

		try {
			jsonString = FileHandler.readFileAsString(path);
			json = new JSONObject(jsonString);
		}
		catch (FileHandlingException ex){
			throw new ModelException("Failed to read data from file", ex);
		}
		
		return json;
	}
	
	/**
	 * Checks for deep equality of two JSONObjects, resolving elements that are JSONObjects or JSONArrays recursively.
	 * <br>
	 * When the element is a string, equality is computed ignoring case.
	 * <br>
	 * If an element is a String value of a number, the ".0" will be stripped from the end before comparison.
	 * 
	 * @param jo1
	 * @param jo2
	 * 
	 * @return true if all leaf objects are equal (case insensitive for Strings), false otherwise
	 * 
	 * @see #equalsIgnoreCase(JSONArray, JSONArray)
	 * @see #equalsJSONComponents(Object, Object)
	 */
	public static boolean equalsIgnoreCase(JSONObject jo1, JSONObject jo2){
		
		if (jo1 == null && jo2 == null){
			return true;
		}
		
		if (jo1 == null && jo2 != null || jo1 != null && jo2 == null){
			return false;
		}
		
		if (jo1.length() != jo2.length()){
			return false;
		}
		
		Iterator<String> keys = jo1.keys();
		while(keys.hasNext()){
			String key = keys.next();
			if (!jo2.has(key)){
				return false;
			}
			
			Object el1 = jo1.opt(key);
			Object el2 = jo2.opt(key);
			boolean equals = equalsJSONComponents(el1, el2);
			if (!equals){
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Checks for deep equality of two JSONArrays, resolving elements that are JSONObjects or JSONArrays recursively.
	 * <br>
	 * When the element is a string, equality is computed ignoring case.
	 * <br>
	 * If an element is a String value of a number, the ".0" will be stripped from the end before comparison.
	 * 
	 * @param ja1
	 * @param ja2
	 * 
	 * @return true if all leaf objects are equal (case insensitive for Strings), false otherwise
	 * 
	 * @see #equalsIgnoreCase(JSONObject, JSONObject)
	 * @see #equalsJSONComponents(Object, Object)
	 */
	public static boolean equalsIgnoreCase(JSONArray ja1, JSONArray ja2){
		
		if (ja1 == null && ja2 == null){
			return true;
		}
		
		if (ja1 == null && ja2 != null || ja1 != null && ja2 == null){
			return false;
		}
		
		if (ja1.length() != ja2.length()){
			return false;
		}
		
		for (int i = 0; i < ja1.length(); i++) {
			Object el1 = ja1.opt(i);
			Object el2 = ja2.opt(i);
			boolean equals = equalsJSONComponents(el1, el2);
			if (!equals){
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Compares two objects ignoring case for Strings, and local implementations for comparing JSONObject and JSONArray.
	 * <br>
	 * If an element is a String value of a number, the ".0" will be stripped from the end before comparison.
	 * <br>
	 * Doubles are formatted to 7 decimal digits.
	 * 
	 * @param o1
	 * @param o2
	 * 
	 * @return true if objects are equal, false otherwise
	 * 
	 * @see #equalsIgnoreCase(JSONArray, JSONArray)
	 * @see #equalsIgnoreCase(JSONObject, JSONObject)
	 */
	private static boolean equalsJSONComponents(Object o1, Object o2){
		
		if (o1 == null && o2 == null){
			return true;
		}
		
		if (o1 == null && o2 != null || o1 != null && o2 == null){
			return false;
		}
		
		if (!o1.getClass().getName().equalsIgnoreCase(o2.getClass().getName())) {
			return false;
		}
		
		if (o1 instanceof JSONObject){
			return equalsIgnoreCase((JSONObject) o1, (JSONObject)o2);
		}
		
		if (o1 instanceof JSONArray){
			return equalsIgnoreCase((JSONArray) o1, (JSONArray)o2);
		}
		
		if (o1 instanceof String){
			
			String s1 = ((String)o1);
			String s2 = ((String)o2);
			
			String numberPattern = "-?[\\d]+(\\.[\\d]+)?";
			
			if (s1.matches(numberPattern)){
				s1 = s1.replaceFirst("\\.0$", "");
			}
			
			if (s2.matches(numberPattern)){
				s2 = s2.replaceFirst("\\.0$", "");
			}
			
			return s1.equalsIgnoreCase(s2);
		}
		
		if (o1 instanceof Double){
			DecimalFormat df = new DecimalFormat("#.#######");
			o1 = df.format(o1);
			o2 = df.format(o2);
			boolean comparison = o1.equals(o2);
			return comparison;
		}
		
		return o1.equals(o2);
	}
	
	public static JSONObject getnnJObject(JSONArray parent, int index){
		JSONObject jObject = parent.optJSONObject(index);
		
		if (jObject == null){
			jObject = new JSONObject();
		}
		
		return jObject;
	}
	
	public static JSONObject getNNJObject(JSONObject parent, Object key){
		JSONObject jObject = parent.optJSONObject(key+"");
		
		if (jObject == null){
			jObject = new JSONObject();
		}
		
		return jObject;
	}
	
	public static JSONArray getNNJArray(JSONObject parent, Object key){
		JSONArray jArray = parent.optJSONArray(key+"");
		
		if (jArray == null){
			jArray = new JSONArray();
		}
		
		return jArray;
	}
	
	public static JSONArray getNNJArray(JSONArray parent, int index){
		JSONArray jArray = parent.optJSONArray(index);
		
		if (jArray == null){
			jArray = new JSONArray();
		}
		
		return jArray;
	}
}
