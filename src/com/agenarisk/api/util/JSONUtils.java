package com.agenarisk.api.util;

import java.util.ArrayList;
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
public class JSONUtils {
	
	public static JSONObject toJSONObject(Object[][] array){
		return new JSONObject(Arrays.stream(array).collect(Collectors.toMap(kv -> (String) kv[0], kv -> kv[1])));
	}
	
	public static String createMissingAttrMessage(JSONException ex){
		if (ex.getMessage().contains("JSONObject[")){
			return "JSON missing required attribute: " + ex.getMessage().replaceFirst("JSONObject\\[\"(.*)\"\\] not found.", "$1");
		}
		else {
			return "JSON missing required attribute at index: " + ex.getMessage().replaceFirst("JSONArray\\[(.*)\\] not found.", "$1");
		}
	}
	
	/**
	 * Converts JSONArray to a List of provided type
	 * @param <T> type of objects expected to be in the list
	 * @param jarray JSONArray array
	 * @param classType Class of items to be in the list
	 * @return List containing items from the JSONArray
	 * @throws JSONException 
	 */
	public static <T> List<T> toList(JSONArray jarray, Class<T> classType) throws JSONException {
		List<T> list = new ArrayList<>();
		for(int i = 0; i < jarray.length(); i++){
			T item = (T) jarray.get(i);
			list.add(item);
		}
		return list;
	}
}
