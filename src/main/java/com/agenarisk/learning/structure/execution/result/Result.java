package com.agenarisk.learning.structure.execution.result;

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
            StructureEvaluation matchedStructure = null;
            PerformanceEvaluation matchedPerformance = null;

            for (StructureEvaluation se : structureEvaluations) {
                if (se.getModelLabel().equals(discovery.getLabel())) {
                    matchedStructure = se;
                    break;
                }
            }

            for (PerformanceEvaluation pe : performanceEvaluations) {
                if (pe.getModelLabel().equals(discovery.getLabel())) {
                    matchedPerformance = pe;
                    break;
                }
            }

            List<Object> row = new ArrayList<>();
            row.add(discovery.getLabel());
            row.add(discovery.isSuccess());
            row.add(discovery.getAlgorithm());
            row.add(discovery.getModelFilePrefix());

            if (matchedStructure != null) {
                row.add(matchedStructure.getLabel());
                row.add(matchedStructure.isSuccess());
                row.add(matchedStructure.getBicScore());
                row.add(matchedStructure.getLogLikelihoodScore());
                row.add(matchedStructure.getComplexityScore());
                row.add(matchedStructure.getFreeParameters());
            } else {
                row.add(""); // structure label
                row.add(""); // structure success
                row.add(""); // bic
                row.add(""); // logLikelihood
                row.add(""); // complexity
                row.add(""); // free parameters
            }

            if (matchedPerformance != null) {
                row.add(matchedPerformance.getLabel());
                row.add(matchedPerformance.isSuccess());
                row.add(matchedPerformance.getAbsoluteError());
                row.add(matchedPerformance.getBrierScore());
                row.add(matchedPerformance.getSphericalScore());
                row.add(matchedPerformance.getMacroAuc() != null ? matchedPerformance.getMacroAuc() : "");
                row.add(matchedPerformance.getMicroAuc() != null ? matchedPerformance.getMicroAuc() : "");
                row.add(matchedPerformance.getMessage());
            } else {
                row.add(""); // performance label
                row.add(""); // performance success
                row.add(""); // absolute error
                row.add(""); // brier score
                row.add(""); // spherical score
                row.add(""); // macro AUC
                row.add(""); // micro AUC
                row.add(""); // message
            }

            row.add(discovery.getModelPath());
            summary.add(row);
        }

        return summary;
    }
}
