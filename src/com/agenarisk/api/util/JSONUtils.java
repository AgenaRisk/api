package com.agenarisk.api.util;

import com.agenarisk.api.exception.ModelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
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
	 * @return JSONObject representation of the given array
	 */
	public static JSONObject toJSONObject(Object[][] array){
		return new JSONObject(Arrays.stream(array).collect(Collectors.toMap(kv -> (String) kv[0], kv -> kv[1])));
	}
	
	/**
	 * This is a helper function to create a message for a JSONException that is formatted as suitable for display to a user.
	 * 
	 * @param ex Exception that has the message to convert
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
	 * Loads Model configuration from an XML file and converts it into suitable JSONObject.
	 * 
	 * @param path XML file path
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
	 * @return Model configuration in JSONObject format
	 * @throws ModelException if failed to read from file
	 * @throws JSONException if JSON structure is invalid or inconsistent
	 */
	public static JSONObject loadModelJSON(String path) throws ModelException, JSONException {
		String jsonString;
		JSONObject json;

		try {
			jsonString = FileHandler.readFileAsString(path);
			json = new JSONObject(jsonString);
		}
		catch (FileHandlingException ex){
			throw new ModelException("Failed to read data from file", ex);
		}
		
		return json;
	}
}
