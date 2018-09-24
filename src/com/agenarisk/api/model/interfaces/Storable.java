package com.agenarisk.api.model.interfaces;

import org.apache.sling.commons.json.JSONObject;

/**
 * Classes that will be stored in a JSON format should implement this.
 * 
 * @author Eugene Dementiev
 */
public interface Storable {
	
	/**
	 * Builds a JSONObject representation of this object.
	 * 
	 * @return JSONObject representation of this object
	 */
	abstract public JSONObject toJSONObject();
}
