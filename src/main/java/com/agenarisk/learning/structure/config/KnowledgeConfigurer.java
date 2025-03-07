package com.agenarisk.learning.structure.config;

import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 * @param <T> The type of parent LearningConfigurer
 */
public class KnowledgeConfigurer<T extends LearningConfigurer> extends Configurer implements ConfigurableFromJson<KnowledgeConfigurer> {
	
	private final T parent;

	public KnowledgeConfigurer(Config config, T parent) {
		super(config);
		this.parent = parent;
	}

	public KnowledgeConfigurer(T parent) {
		super();
		this.parent = parent;
	}

	/**
	 * If set, custom knowledge settings will be applied
	 * @return 
	 */
	public Boolean isCustomKnowledgeEnabled() {
        return config.getKnowledgeConfigured();
    }

    public KnowledgeConfigurer<T> setCustomKnowledgeEnabled(Boolean knowledgeConfigured) {
        config.setKnowledgeConfigured(knowledgeConfigured);
        return this;
    }

    // Getter and Setter for Directed Constraints
    public Boolean isConstraintsDirectedEnabled() {
        return config.getConstraintsDirectedEnabled();
    }

    public KnowledgeConfigurer<T> setConstraintsDirectedEnabled(Boolean constraintsDirectedEnabled) {
        config.setConstraintsDirectedEnabled(constraintsDirectedEnabled);
        return this;
    }

    // Getter and Setter for Undirected Constraints
    public Boolean isConstraintsUndirectedEnabled() {
        return config.getConstraintsUndirectedEnabled();
    }

    public KnowledgeConfigurer<T> setConstraintsUndirectedEnabled(Boolean constraintsUndirectedEnabled) {
        config.setConstraintsUndirectedEnabled(constraintsUndirectedEnabled);
        return this;
    }

    // Getter and Setter for Forbidden Constraints
    public Boolean isConstraintsForbiddenEnabled() {
        return config.getConstraintsForbiddenEnabled();
    }

    public KnowledgeConfigurer<T> setConstraintsForbiddenEnabled(Boolean constraintsForbiddenEnabled) {
        config.setConstraintsForbiddenEnabled(constraintsForbiddenEnabled);
        return this;
    }

    // Getter and Setter for Temporal Constraints
    public Boolean isConstraintsTemporalEnabled() {
        return config.getConstraintsTemporalEnabled();
    }

    public KnowledgeConfigurer<T> setConstraintsTemporalEnabled(Boolean constraintsTemporalEnabled) {
        config.setConstraintsTemporalEnabled(constraintsTemporalEnabled);
        return this;
    }

    // Getter and Setter for Prohibit Edges in Same Temporal Tier
    public Boolean isConstraintsProhibitEdgesSameTemporalTier() {
        return config.getConstraintsProhibitEdgesSameTemporalTier();
    }

    public KnowledgeConfigurer<T> setConstraintsProhibitEdgesSameTemporalTier(Boolean constraintsProhibitEdgesSameTemporalTier) {
        config.setConstraintsProhibitEdgesSameTemporalTier(constraintsProhibitEdgesSameTemporalTier);
        return this;
    }

    // Getter and Setter for BDN Constraints
    public Boolean isConstraintsBDN() {
        return config.getConstraintsBDN();
    }

    public KnowledgeConfigurer<T> setConstraintsBDN(Boolean constraintsBDN) {
        config.setConstraintsBDN(constraintsBDN);
        return this;
    }

    // Getter and Setter for BDN Guarantee Constraints
    public Boolean isConstraintsBDNGuarantee() {
        return config.getConstraintsBDNGuarantee();
    }

    public KnowledgeConfigurer<T> setConstraintsBDNGuarantee(Boolean constraintsBDNGuarantee) {
        config.setConstraintsBDNGuarantee(constraintsBDNGuarantee);
        return this;
    }

    // Getter and Setter for Initial Graph Constraints
    public Boolean isConstraintsInitialGraph() {
        return config.getConstraintsInitialGraph();
    }

    public KnowledgeConfigurer<T> setConstraintsInitialGraph(Boolean constraintsInitialGraph) {
        config.setConstraintsInitialGraph(constraintsInitialGraph);
        return this;
    }

    /**
	 * If set, all variables in the training data must be connected within the same graph and no disjointed variables are allowed
	 * @return
	 */
    public Boolean isAllVariablesRelevant() {
        return config.getAllVariablesRelevant();
    }

	/**
	 * If set, all variables in the training data must be connected within the same graph and no disjointed variables are allowed
	 * @param allVariablesRelevant
	 * @return
	 */
    public KnowledgeConfigurer<T> setAllVariablesRelevant(Boolean allVariablesRelevant) {
        config.setAllVariablesRelevant(allVariablesRelevant);
        return this;
    }

    // Getter and Setter for Target Penalty Reduction Rate Enabled
    public Boolean isConstraintsTargetPenaltyReductionRateEnabled() {
        return config.getConstraintsTargetPenaltyReductionRateEnabled();
    }

    public KnowledgeConfigurer<T> setConstraintsTargetPenaltyReductionRateEnabled(Boolean constraintsTargetPenaltyReductionRateEnabled) {
        config.setConstraintsTargetPenaltyReductionRateEnabled(constraintsTargetPenaltyReductionRateEnabled);
        return this;
    }

    // Getter and Setter for Target Penalty Reduction Rate
    public int getConstraintsTargetPenaltyReductionRate() {
        return config.getConstraintsTargetPenaltyReductionRate();
    }

    public KnowledgeConfigurer<T> setConstraintsTargetPenaltyReductionRate(int constraintsTargetPenaltyReductionRate) {
        config.setConstraintsTargetPenaltyReductionRate(constraintsTargetPenaltyReductionRate);
        return this;
    }

	public T applyKnowledge(){
		return parent;
	}
	
	@Override
	public KnowledgeConfigurer configureFromJson(JSONObject jConfig) {
		if (!jConfig.has("knowledge")){
			return this;
		}
		
		JSONObject jKnowledge = jConfig.getJSONObject("knowledge");
		setCustomKnowledgeEnabled(true);
		setAllVariablesRelevant(jKnowledge.optBoolean("variablesAreRelevant", false));
		setConstraintsTargetPenaltyReductionRate(jKnowledge.optInt("dimensionalityReductionRate", 2));
		setConstraintsProhibitEdgesSameTemporalTier(jKnowledge.optBoolean("prohibitConnectionsSameTemporalTier", false));
		
		try {
			if (jKnowledge.has("reduceDimensionalityPenaltyForVariables")){
				ArrayList<List<Object>> lines = new ArrayList<>();
				lines.add(Arrays.asList("ID", "Target node"));
				JSONArray jArray = jKnowledge.getJSONArray("reduceDimensionalityPenaltyForVariables");
				for(int i = 0; i < jArray.length(); i+=1){
					lines.add(Arrays.asList(String.valueOf(i+1), jArray.getString(i)));
				}
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_TARGET);
				CsvWriter.writeCsv(lines, filePath);
				TempFileCleanup.registerTempFile(filePath.toFile(), config);
				config.setConstraintsTargetPenaltyReductionRateEnabled(true);
			}
			
			if (jKnowledge.has("connectionsInitialGuess")){
				ArrayList<List<Object>> lines = new ArrayList<>();
				lines.add(Arrays.asList("ID", "Parent", "Child"));
				JSONArray jArray = jKnowledge.getJSONArray("connectionsInitialGuess");
				for(int i = 0; i < jArray.length(); i+=1){
					JSONArray jRow = jArray.getJSONArray(i);
					lines.add(Arrays.asList(String.valueOf(i+1), jRow.getString(0), jRow.getString(1)));
				}
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_GRAPH);
				CsvWriter.writeCsv(lines, filePath);
				TempFileCleanup.registerTempFile(filePath.toFile(), config);
				config.setConstraintsInitialGraph(true);
			}
			
			if (jKnowledge.has("connectionsDirected")){
				ArrayList<List<Object>> lines = new ArrayList<>();
				lines.add(Arrays.asList("ID", "Parent", "Child"));
				JSONArray jArray = jKnowledge.getJSONArray("connectionsDirected");
				for(int i = 0; i < jArray.length(); i+=1){
					JSONArray jRow = jArray.getJSONArray(i);
					lines.add(Arrays.asList(String.valueOf(i+1), jRow.getString(0), jRow.getString(1)));
				}
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_DIRECTED);
				CsvWriter.writeCsv(lines, filePath);
				TempFileCleanup.registerTempFile(filePath.toFile(), config);
				config.setConstraintsDirectedEnabled(true);
			}
			
			if (jKnowledge.has("connectionsUndirected")){
				ArrayList<List<Object>> lines = new ArrayList<>();
				lines.add(Arrays.asList("ID", "Var1", "Var2"));
				JSONArray jArray = jKnowledge.getJSONArray("connectionsUndirected");
				for(int i = 0; i < jArray.length(); i+=1){
					JSONArray jRow = jArray.getJSONArray(i);
					lines.add(Arrays.asList(String.valueOf(i+1), jRow.getString(0), jRow.getString(1)));
				}
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_UNDIRECTED);
				CsvWriter.writeCsv(lines, filePath);
				TempFileCleanup.registerTempFile(filePath.toFile(), config);
				config.setConstraintsUndirectedEnabled(true);
			}
			
			if (jKnowledge.has("connectionsForbidden")){
				ArrayList<List<Object>> lines = new ArrayList<>();
				lines.add(Arrays.asList("ID", "Var1", "Var2"));
				JSONArray jArray = jKnowledge.getJSONArray("connectionsForbidden");
				for(int i = 0; i < jArray.length(); i+=1){
					JSONArray jRow = jArray.getJSONArray(i);
					lines.add(Arrays.asList(String.valueOf(i+1), jRow.getString(0), jRow.getString(1)));
				}
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_FORBIDDEN);
				CsvWriter.writeCsv(lines, filePath);
				TempFileCleanup.registerTempFile(filePath.toFile(), config);
				config.setConstraintsForbiddenEnabled(true);
			}
			
			if (jKnowledge.has("connectionsTemporal")){
				ArrayList<List<Object>> lines = new ArrayList<>();
				JSONArray jTiers = jKnowledge.getJSONArray("connectionsTemporal");
				ArrayList<Object> headers = new ArrayList<>();
				headers.add("ID");
				for(int i = 0; i < jTiers.length(); i += 1){
					headers.add("Tier " + (i+1));
					JSONArray jTier = jTiers.getJSONArray(i);
					for(int j = 0; j < jTier.length(); j += 1 ){
						List<Object> line;
						if (lines.size() - 1 <= j){
							line = new ArrayList<>();
							line.add(String.valueOf(j + 1));
							lines.add(line);
						}
						else {
							line = lines.get(j+1);
						}
						line.add(jTier.getString(j));
					}
				}
				headers.add("END");
				lines.add(0, headers);
				
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_TEMPORAL);
				CsvWriter.writeCsv(lines, filePath);
				TempFileCleanup.registerTempFile(filePath.toFile(), config);
				config.setConstraintsTemporalEnabled(true);
			}
			
		}
		catch(IOException ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
		
		
		return this;
	}
}
