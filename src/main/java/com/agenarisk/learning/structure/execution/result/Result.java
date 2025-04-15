package com.agenarisk.learning.structure.execution.result;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */

public class Result {
    private final ArrayList<Discovery> discoveries = new ArrayList<>();
    private final ArrayList<StructureEvaluation> structureEvaluations = new ArrayList<>();

    public ArrayList<Discovery> getDiscoveries() {
        return discoveries;
    }

    public ArrayList<StructureEvaluation> getStructureEvaluations() {
        return structureEvaluations;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        JSONArray discoveryArray = new JSONArray();
        JSONArray evaluationArray = new JSONArray();

        for (Discovery discovery : discoveries) {
            discoveryArray.put(discovery.toJson());
        }

        for (StructureEvaluation evaluation : structureEvaluations) {
            evaluationArray.put(evaluation.toJson());
        }

        json.put("discoveries", discoveryArray);
        json.put("structureEvaluations", evaluationArray);
        return json;
    }

    public ArrayList<List<Object>> getSummary() {
        ArrayList<List<Object>> summary = new ArrayList<>();

        if (structureEvaluations.isEmpty()) {
            for (Discovery discovery : discoveries) {
                List<Object> row = new ArrayList<>();
                row.add(discovery.getLabel());
				row.add(discovery.isSuccess());
                row.add(discovery.getAlgorithm());
                row.add(discovery.getModelFilePrefix());
                row.add("");
                row.add("");
                row.add("");
                row.add("");
				row.add("");
                row.add("");
                row.add(discovery.getModelPath());
                summary.add(row);
            }
            return summary;
        }

        for (Discovery discovery : discoveries) {
            boolean matchFound = false;
            for (StructureEvaluation evaluation : structureEvaluations) {
                if (evaluation.getModelLabel().equals(discovery.getLabel())) {
                    List<Object> row = new ArrayList<>();
                    row.add(discovery.getLabel());
					row.add(discovery.isSuccess()+"");
                    row.add(discovery.getAlgorithm());
                    row.add(discovery.getModelFilePrefix());
                    row.add(evaluation.getLabel());
					row.add(evaluation.isSuccess()+"");
                    row.add(evaluation.getBicScore());
                    row.add(evaluation.getLogLikelihoodScore());
                    row.add(evaluation.getComplexityScore());
                    row.add(evaluation.getFreeParameters());
                    row.add(discovery.getModelPath());
                    summary.add(row);
                    matchFound = true;
                }
            }
            if (!matchFound) {
                List<Object> row = new ArrayList<>();
                row.add(discovery.getLabel());
				row.add(discovery.isSuccess());
                row.add(discovery.getAlgorithm());
                row.add(discovery.getModelFilePrefix());
                row.add("");
                row.add("");
				row.add("");
                row.add("");
                row.add("");
                row.add("");
                row.add(discovery.getModelPath());
                summary.add(row);
            }
        }

        return summary;
    }
}
