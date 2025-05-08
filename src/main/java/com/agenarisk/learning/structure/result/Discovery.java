package com.agenarisk.learning.structure.result;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */

public class Discovery {

	private String label = "";
    private String algorithm = "";
    private boolean success = false;
    private String message = "";
    private JSONObject model = new JSONObject();
    private String modelPath = "";
	private String modelFilePrefix = "";
	private boolean average = false;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public JSONObject getModel() {
        return model;
    }

    public void setModel(JSONObject model) {
        this.model = model;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

	public String getModelFilePrefix() {
		return modelFilePrefix;
	}

	public void setModelFilePrefix(String modelFilePrefix) {
		this.modelFilePrefix = modelFilePrefix;
	}

	public boolean isAverage() {
		return average;
	}

	public void setAverage(boolean average) {
		this.average = average;
	}
	
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("label", label);
        json.put("algorithm", algorithm);
        json.put("success", success);
        json.put("message", message);
        json.put("model", model);
        json.put("modelPath", modelPath);
		json.put("modelFilePrefix", modelFilePrefix);
		json.put("average", average);
        return json;
    }
}
