package com.agenarisk.api.model.interfaces;

import org.apache.sling.commons.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public interface Storable {
	
	abstract public JSONObject toJSON();
}
