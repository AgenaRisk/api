package com.agenarisk.learning.structure.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class PerformanceEvaluation {
    private String label = "";
    private boolean success = false;
    private String message = "";
    private String modelLabel = "";
    private String target = ""; // the target node this result is for (per-target results)
    // Per-target breakdown (populated on the model-level aggregate result only).
    private final List<PerformanceEvaluation> targetResults = new ArrayList<>();
    
	private double brierScore = 1;
	private double absoluteError = 1;
	private double sphericalScore = 0;
	
	private final Map<String, List<Double>> rocScores = new HashMap<>(); // class label -> list of predicted probabilities
	private final Map<String, List<Integer>> rocTruths = new HashMap<>(); // class label -> list of binary actuals
	private Map<String, Double> rocAucs = new HashMap<>(); // class label -> AUC value
	private Map<String, List<double[]>> rocPoints = new HashMap<>(); // class label -> list of (FPR, TPR) pairs
	private Double macroAuc = null; // average of rocAucs
	private Double microAuc = null; // micro-average AUC

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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target != null ? target : "";
    }

    public List<PerformanceEvaluation> getTargetResults() {
        return targetResults;
    }

    public double getBrierScore() {
        return brierScore;
    }

    public void setBrierScore(double brierScore) {
        this.brierScore = brierScore;
    }

    public double getAbsoluteError() {
        return absoluteError;
    }

    public void setAbsoluteError(double absoluteError) {
        this.absoluteError = absoluteError;
    }

    public double getSphericalScore() {
        return sphericalScore;
    }

    public void setSphericalScore(double sphericalScore) {
        this.sphericalScore = sphericalScore;
    }

	public Double getMacroAuc() {
		return macroAuc;
	}

	public Double getMicroAuc() {
		return microAuc;
	}
	
	public Map<String, List<Double>> getRocScores() {
		return rocScores;
	}

	public Map<String, List<Integer>> getRocTruths() {
		return rocTruths;
	}

	public Map<String, Double> getRocAucs() {
		return rocAucs;
	}

	public Map<String, List<double[]>> getRocPoints() {
		return rocPoints;
	}
	
	public void addRocAuc(String classLabel, double auc) {
		if (this.rocAucs == null) {
			this.rocAucs = new HashMap<>();
		}
		this.rocAucs.put(classLabel, auc);
	}

	public void addRocCurve(String classLabel, List<double[]> points) {
		if (this.rocPoints == null) {
			this.rocPoints = new HashMap<>();
		}
		this.rocPoints.put(classLabel, points);
	}

	public void setMacroAuc(double macroAuc) {
		this.macroAuc = macroAuc;
	}

	public void setMicroAuc(double microAuc) {
		this.microAuc = microAuc;
	}
	
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("label", label);
        json.put("success", success);
        json.put("message", message);
        json.put("modelLabel", modelLabel);
        if (!target.isEmpty()) {
            json.put("target", target);
        }
        json.put("absoluteError", absoluteError);
		json.put("brierScore", brierScore);
        json.put("sphericalScore", sphericalScore);
		if (macroAuc != null){
			json.put("macroAuc", macroAuc);
		}
		if (microAuc != null){
			json.put("microAuc", microAuc);
		}

		if (!rocAucs.isEmpty()){
			JSONObject aucJson = new JSONObject();
			for (Map.Entry<String, Double> entry : rocAucs.entrySet()) {
				aucJson.put(entry.getKey(), entry.getValue());
			}
			json.put("rocAucs", aucJson);
		}
		
		if (!rocPoints.isEmpty()){
			JSONObject rocPointsJson = new JSONObject();
			for (Map.Entry<String, List<double[]>> entry : rocPoints.entrySet()) {
				JSONArray pointsArray = new JSONArray();
				for (double[] pair : entry.getValue()) {
					JSONArray point = new JSONArray();
					point.put(pair[0]);
					point.put(pair[1]);
					pointsArray.put(point);
				}
				rocPointsJson.put(entry.getKey(), pointsArray);
			}
			json.put("rocPoints", rocPointsJson);
		}

		if (!targetResults.isEmpty()){
			JSONArray targetsArray = new JSONArray();
			for (PerformanceEvaluation te : targetResults) {
				targetsArray.put(te.toJson());
			}
			json.put("targets", targetsArray);
		}

		return json;
    }
	
}
