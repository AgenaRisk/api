package com.agenarisk.learning.structure.result;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */

public class StructureEvaluation {
    
    private String label = "";
    private boolean success = false;
    private String message = "";
    private String modelLabel = "";
    private double logLikelihoodScore = 0;
    private double bicScore = 0;
    private long freeParameters = 0;
    private double complexityScore = 0;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public String getModelLabel() {
        return modelLabel;
    }

    public void setModelLabel(String modelLabel) {
        this.modelLabel = modelLabel;
    }

    public double getLogLikelihoodScore() {
        return logLikelihoodScore;
    }

    public void setLogLikelihoodScore(double logLikelihoodScore) {
        this.logLikelihoodScore = logLikelihoodScore;
    }

    public double getBicScore() {
        return bicScore;
    }

    public void setBicScore(double bicScore) {
        this.bicScore = bicScore;
    }

    public long getFreeParameters() {
        return freeParameters;
    }

    public void setFreeParameters(long freeParameters) {
        this.freeParameters = freeParameters;
    }

    public double getComplexityScore() {
        return complexityScore;
    }

    public void setComplexityScore(double complexityScore) {
        this.complexityScore = complexityScore;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("label", label);
        json.put("success", success);
        json.put("message", message);
        json.put("modelLabel", modelLabel);
        json.put("logLikelihoodScore", logLikelihoodScore);
        json.put("bicScore", bicScore);
        json.put("freeParameters", freeParameters);
        json.put("complexityScore", complexityScore);
        return json;
    }
}
