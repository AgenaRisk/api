package misc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.JSONTokener;

/**
 * JObject is an extension of JSONObject class that allows passing Objects as key parameter, in which case toString() of the passed object will be used as the key.
 * 
 * @author Eugene Dementiev
 */
public class JObject extends JSONObject {

	/**
	 * Builds a list of keys for this JObject.
	 * 
	 * @return list of keys
	 */
	public List<String> getKeys(){
		return getKeys(this);
	}
	
	/**
	 * Builds a list of keys for the given JSONObject.
	 * 
	 * @param jo JSONObject to get keys from
	 * 
	 * @return list of keys
	 */
	public static List<String> getKeys(JSONObject jo){
		List<String> keys = new ArrayList<>();
		while(jo.keys().hasNext()){
			String key = jo.keys().next();
			keys.add(key);
		}
		return keys;
	}
	
	public JObject() {
	}
	
	/**
	 * Creates a JObject by copying all key-value pairs from given JSONObject
	 * 
	 * @param jo JSONObject to copy from
	 */
	public JObject(JSONObject jo) {
		this();
		
		for(String key: getKeys(jo)){
			try {
				putOpt(key, jo.opt(key));
			}
			catch (JSONException ex){
				// Ignore as impossible
			}
		}
	}

	public JObject(JSONObject jo, String[] sa) throws JSONException {
		super(jo, sa);
	}

	public JObject(JSONTokener x) throws JSONException {
		super(x);
	}

	public JObject(Map<String, ?> map) {
		super(map);
	}

	public JObject(Object object, String[] names) {
		super(object, names);
	}

	public JObject(String string) throws JSONException {
		super(string);
	}

	public JSONObject putOpt(Object key, Object value) throws JSONException {
		return super.putOpt(key.toString(), value);
	}

	public JSONObject put(Object key, Object value) throws JSONException {
		return super.put(key.toString(), value);
	}

	public JSONObject put(Object key, Map<String, ?> value) throws JSONException {
		return super.put(key.toString(), value);
	}

	public JSONObject put(Object key, long value) throws JSONException {
		return super.put(key.toString(), value);
	}

	public JSONObject put(Object key, int value) throws JSONException {
		return super.put(key.toString(), value);
	}

	public JSONObject put(Object key, double value) throws JSONException {
		return super.put(key.toString(), value);
	}

	public JSONObject put(Object key, boolean value) throws JSONException {
		return super.put(key.toString(), value);
	}

	public JSONObject put(Object key, Collection<?> value) throws JSONException {
		return super.put(key.toString(), value);
	}

	public Object remove(Object key) {
		return super.remove(key.toString());
	}

	public String optString(Object key, String defaultValue) {
		return super.optString(key.toString(), defaultValue);
	}

	public String optString(Object key) {
		return super.optString(key.toString());
	}

	public long optLong(Object key, long defaultValue) {
		return super.optLong(key.toString(), defaultValue);
	}

	public long optLong(Object key) {
		return super.optLong(key.toString());
	}

	public JSONObject optJSONObject(Object key) {
		return super.optJSONObject(key.toString());
	}

	public JSONArray optJSONArray(Object key) {
		return super.optJSONArray(key.toString());
	}

	public int optInt(Object key, int defaultValue) {
		return super.optInt(key.toString(), defaultValue);
	}

	public int optInt(Object key) {
		return super.optInt(key.toString());
	}

	public double optDouble(Object key, double defaultValue) {
		return super.optDouble(key.toString(), defaultValue);
	}

	public double optDouble(Object key) {
		return super.optDouble(key.toString());
	}

	public boolean optBoolean(Object key, boolean defaultValue) {
		return super.optBoolean(key.toString(), defaultValue);
	}

	public boolean optBoolean(Object key) {
		return super.optBoolean(key.toString());
	}

	public Object opt(Object key) {
		return super.opt(key.toString());
	}

	public boolean isNull(Object key) {
		return super.isNull(key.toString());
	}

	public boolean has(Object key) {
		return super.has(key.toString());
	}

	public String getString(Object key) throws JSONException {
		return super.getString(key.toString());
	}

	public long getLong(Object key) throws JSONException {
		return super.getLong(key.toString());
	}

	public JSONObject getJSONObject(Object key) throws JSONException {
		return super.getJSONObject(key.toString());
	}
	
	public JObject getJObject(Object key) throws JSONException {
		return (JObject)this.getJSONObject(key.toString());
	}

	public JSONArray getJSONArray(Object key) throws JSONException {
		return super.getJSONArray(key.toString());
	}

	public int getInt(Object key) throws JSONException {
		return super.getInt(key.toString());
	}

	public double getDouble(Object key) throws JSONException {
		return super.getDouble(key.toString());
	}

	public boolean getBoolean(Object key) throws JSONException {
		return super.getBoolean(key.toString());
	}

	public Object get(Object key) throws JSONException {
		return super.get(key.toString());
	}

	public JSONObject append(Object key, Object value) throws JSONException {
		return super.append(key.toString(), value);
	}

	public JSONObject accumulate(Object key, Object value) throws JSONException {
		return super.accumulate(key.toString(), value);
	}
	
}
