package com.agenarisk.learning.structure.result;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class Result {
    private final ArrayList<Discovery> discoveries = new ArrayList<>();
    private final ArrayList<StructureEvaluation> structureEvaluations = new ArrayList<>();
    private final ArrayList<PerformanceEvaluation> performanceEvaluations = new ArrayList<>();

    public ArrayList<Discovery> getDiscoveries() {
        return discoveries;
    }

    public ArrayList<StructureEvaluation> getStructureEvaluations() {
        return structureEvaluations;
    }

    public ArrayList<PerformanceEvaluation> getPerformanceEvaluations() {
        return performanceEvaluations;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        JSONArray discoveryArray = new JSONArray();
        JSONArray structureArray = new JSONArray();
        JSONArray performanceArray = new JSONArray();

        for (Discovery discovery : discoveries) {
            discoveryArray.put(discovery.toJson());
        }

        for (StructureEvaluation structureEval : structureEvaluations) {
            structureArray.put(structureEval.toJson());
        }

        for (PerformanceEvaluation perfEval : performanceEvaluations) {
            performanceArray.put(perfEval.toJson());
        }

        json.put("discoveries", discoveryArray);
        json.put("structureEvaluations", structureArray);
        json.put("performanceEvaluations", performanceArray);

        return json;
    }

    public ArrayList<List<Object>> getSummary() {
        ArrayList<List<Object>> summary = new ArrayList<>();

        for (Discovery discovery : discoveries) {
            // Add row for each structure evaluation
            boolean hasStructureEval = false;
            for (StructureEvaluation se : structureEvaluations) {
                if (se.getModelLabel().equals(discovery.getLabel())) {
                    hasStructureEval = true;
                    List<Object> row = new ArrayList<>();
                    row.add(discovery.getLabel());
                    row.add(discovery.isSuccess());
                    row.add(discovery.getAlgorithm());
                    row.add(discovery.getModelFilePrefix());
                    row.add(se.getLabel());
                    row.add(se.isSuccess());
                    row.add(se.getBicScore());
                    row.add(se.getLogLikelihoodScore());
                    row.add(se.getComplexityScore());
                    row.add(se.getFreeParameters());
                    row.add(""); // performance label
                    row.add(""); // performance success
                    row.add(""); // absolute error
                    row.add(""); // brier score
                    row.add(""); // spherical score
                    row.add(""); // macro AUC
                    row.add(""); // micro AUC
                    row.add(""); // message
                    row.add(discovery.getModelPath());
                    summary.add(row);
                }
            }

            // Add row for each performance evaluation
            boolean hasPerformanceEval = false;
            for (PerformanceEvaluation pe : performanceEvaluations) {
                if (pe.getModelLabel().equals(discovery.getLabel())) {
                    hasPerformanceEval = true;
                    List<Object> row = new ArrayList<>();
                    row.add(discovery.getLabel());
                    row.add(discovery.isSuccess());
                    row.add(discovery.getAlgorithm());
                    row.add(discovery.getModelFilePrefix());
                    row.add(""); // structure label
                    row.add(""); // structure success
                    row.add(""); // bic
                    row.add(""); // logLikelihood
                    row.add(""); // complexity
                    row.add(""); // free parameters
                    row.add(pe.getLabel());
                    row.add(pe.isSuccess());
                    row.add(pe.getAbsoluteError());
                    row.add(pe.getBrierScore());
                    row.add(pe.getSphericalScore());
                    row.add(pe.getMacroAuc() != null ? pe.getMacroAuc() : "");
                    row.add(pe.getMicroAuc() != null ? pe.getMicroAuc() : "");
                    row.add(pe.getMessage());
                    row.add(discovery.getModelPath());
                    summary.add(row);
                }
            }

            // If no evaluations exist for this discovery, add one row
            if (!hasStructureEval && !hasPerformanceEval) {
                List<Object> row = new ArrayList<>();
                row.add(discovery.getLabel());
                row.add(discovery.isSuccess());
                row.add(discovery.getAlgorithm());
                row.add(discovery.getModelFilePrefix());
                row.add(""); // structure label
                row.add(""); // structure success
                row.add(""); // bic
                row.add(""); // logLikelihood
                row.add(""); // complexity
                row.add(""); // free parameters
                row.add(""); // performance label
                row.add(""); // performance success
                row.add(""); // absolute error
                row.add(""); // brier score
                row.add(""); // spherical score
                row.add(""); // macro AUC
                row.add(""); // micro AUC
                row.add(""); // message
                row.add(discovery.getModelPath());
                summary.add(row);
            }
        }

        return summary;
    }
}
