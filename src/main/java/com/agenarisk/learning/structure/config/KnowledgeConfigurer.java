package com.agenarisk.learning.structure.config;

import com.agenarisk.api.util.CsvWriter;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.logger.BLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.util.io.MinervaProperties;

/**
 *
 * @author Eugene Dementiev
 * @param <T> The type of parent LearningConfigurer
 */
public class KnowledgeConfigurer<T extends LearningConfigurer> extends Configurer<KnowledgeConfigurer<T>> implements ConfigurableFromJson<KnowledgeConfigurer> {
	
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

    // Getter and Setter for Directed Forbidden Constraints (forbids Parent -> Child only, unlike the plain/undirected Forbidden Constraints above)
    public Boolean isConstraintsForbiddenDirectedEnabled() {
        return config.getConstraintsForbiddenDirectedEnabled();
    }

    public KnowledgeConfigurer<T> setConstraintsForbiddenDirectedEnabled(Boolean constraintsForbiddenDirectedEnabled) {
        config.setConstraintsForbiddenDirectedEnabled(constraintsForbiddenDirectedEnabled);
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
		logConstraintContradictions(jKnowledge);
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
				registerTempFileConditional(filePath.toFile());
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
				registerTempFileConditional(filePath.toFile());
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
				registerTempFileConditional(filePath.toFile());
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
				registerTempFileConditional(filePath.toFile());
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
				registerTempFileConditional(filePath.toFile());
				config.setConstraintsForbiddenEnabled(true);
			}

			if (jKnowledge.has("connectionsForbiddenDirected")){
				ArrayList<List<Object>> lines = new ArrayList<>();
				lines.add(Arrays.asList("ID", "Parent", "Child"));
				JSONArray jArray = jKnowledge.getJSONArray("connectionsForbiddenDirected");
				for(int i = 0; i < jArray.length(); i+=1){
					JSONArray jRow = jArray.getJSONArray(i);
					lines.add(Arrays.asList(String.valueOf(i+1), jRow.getString(0), jRow.getString(1)));
				}
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_FORBIDDEN_DIRECTED);
				CsvWriter.writeCsv(lines, filePath);
				registerTempFileConditional(filePath.toFile());
				config.setConstraintsForbiddenDirectedEnabled(true);
			}

			if (jKnowledge.has("connectionsTemporal")){
				ArrayList<List<String>> lines = new ArrayList<>();
				JSONArray jTiers = jKnowledge.getJSONArray("connectionsTemporal");
				ArrayList<String> headers = new ArrayList<>();
				headers.add("ID");
				for(int i = 0; i < jTiers.length(); i += 1){
					int tierIndex = i+1;
					headers.add("Tier " + tierIndex);
					JSONArray jTier = jTiers.getJSONArray(i);
					for(int j = 0; j < jTier.length(); j += 1 ){
						List<String> line;
						if (lines.size() == j){
							line = new ArrayList<>();
							line.add(String.valueOf(j + 1));
							lines.add(line);
						}
						else {
							line = lines.get(j);
						}
						
						int requiredPadding = tierIndex-line.size();
						for(int k = 0; k < requiredPadding; k++){
							line.add("");
						}
						
						line.add(jTier.getString(j));
					}
				}
				headers.add("END");
				lines.add(0, headers);
				
				Path filePath = config.getPathInput().resolve(Config.FILE_CONSTRAINTS_TEMPORAL);
				CsvWriter.writeCsv(lines, filePath);
				registerTempFileConditional(filePath.toFile());
				config.setConstraintsTemporalEnabled(true);
			}
			
		}
		catch(IOException ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
		
		return this;
	}

	/**
	 * Logs a warning for every pair of knowledge constraints that contradict
	 * each other. None of these are rejected/blocked here - each one just
	 * behaves per the actual precedence in the search algorithms (directed and
	 * undirected connections are force-seeded into the initial graph and
	 * always win; forbidden and temporal-tier ordering only ever restrict
	 * which NEW edges the search may propose, so they have no effect on an
	 * edge that's already been force-seeded). Surfacing that precedence here
	 * means a contradiction is at least visible in the log, rather than
	 * silently doing something different from what was asked for both
	 * constraints.
	 */
	private void logConstraintContradictions(JSONObject jKnowledge) {
		List<String[]> directedPairs = readPairs(jKnowledge, "connectionsDirected");
		List<String[]> undirectedPairs = readPairs(jKnowledge, "connectionsUndirected");
		List<String[]> forbiddenPairs = readPairs(jKnowledge, "connectionsForbidden");
		List<String[]> forbiddenDirectedPairs = readPairs(jKnowledge, "connectionsForbiddenDirected");

		Set<String> directedKeys = new HashSet<>();
		for (String[] pair : directedPairs) {
			directedKeys.add(pairKey(pair[0], pair[1]));
		}
		Set<String> forbiddenKeys = new HashSet<>();
		for (String[] pair : forbiddenPairs) {
			forbiddenKeys.add(pairKey(pair[0], pair[1]));
		}
		// Ordered (direction-sensitive) key, unlike pairKey above - a directed-
		// forbidden constraint only blocks one specific direction, so whether it
		// contradicts another constraint depends on which way that one points too.
		Set<String> forbiddenDirectedOrderedKeys = new HashSet<>();
		for (String[] pair : forbiddenDirectedPairs) {
			forbiddenDirectedOrderedKeys.add(orderedPairKey(pair[0], pair[1]));
		}

		Map<String, Integer> tierOf = new HashMap<>();
		if (jKnowledge.has("connectionsTemporal")) {
			JSONArray jTiers = jKnowledge.getJSONArray("connectionsTemporal");
			for (int t = 0; t < jTiers.length(); t++) {
				JSONArray jTier = jTiers.getJSONArray(t);
				for (int v = 0; v < jTier.length(); v++) {
					tierOf.put(jTier.getString(v), t);
				}
			}
		}
		boolean prohibitSameTier = jKnowledge.optBoolean("prohibitConnectionsSameTemporalTier", false);

		// Directed vs undirected on the same pair.
		for (String[] pair : undirectedPairs) {
			if (directedKeys.contains(pairKey(pair[0], pair[1]))) {
				BLogger.out.println("WARNING: Directed and undirected constraints both specify a connection between \""
						+ pair[0] + "\" and \"" + pair[1] + "\" - the directed constraint takes precedence "
						+ "(force-seeded first), the undirected constraint has no additional effect for this pair.");
			}
		}

		// Directed/undirected vs forbidden on the same pair.
		for (String[] pair : directedPairs) {
			if (forbiddenKeys.contains(pairKey(pair[0], pair[1]))) {
				BLogger.out.println("WARNING: \"" + pair[0] + "\" -> \"" + pair[1] + "\" is both a directed connection "
						+ "and a forbidden connection - the directed constraint takes precedence and this edge will be "
						+ "created despite being marked forbidden.");
			}
		}
		for (String[] pair : undirectedPairs) {
			if (forbiddenKeys.contains(pairKey(pair[0], pair[1]))) {
				BLogger.out.println("WARNING: \"" + pair[0] + "\" - \"" + pair[1] + "\" is both an undirected connection "
						+ "and a forbidden connection - the undirected constraint takes precedence and an edge will be "
						+ "created despite being marked forbidden.");
			}
		}

		// Directed vs directed-forbidden on the exact same direction (a reversed
		// directed constraint does NOT contradict, since directed-forbidden only
		// blocks the one direction it names).
		for (String[] pair : directedPairs) {
			if (forbiddenDirectedOrderedKeys.contains(orderedPairKey(pair[0], pair[1]))) {
				BLogger.out.println("WARNING: \"" + pair[0] + "\" -> \"" + pair[1] + "\" is both a directed connection "
						+ "and a directed-forbidden connection in the same direction - the directed constraint takes "
						+ "precedence and this edge will be created despite being marked forbidden.");
			}
		}

		// Undirected vs directed-forbidden - contradicts regardless of which way
		// the directed-forbidden pair points, since undirected can seed either
		// orientation.
		for (String[] pair : undirectedPairs) {
			if (forbiddenDirectedOrderedKeys.contains(orderedPairKey(pair[0], pair[1]))
					|| forbiddenDirectedOrderedKeys.contains(orderedPairKey(pair[1], pair[0]))) {
				BLogger.out.println("WARNING: \"" + pair[0] + "\" - \"" + pair[1] + "\" is both an undirected connection "
						+ "and a directed-forbidden connection - the undirected constraint takes precedence and an edge "
						+ "may be created in the forbidden direction.");
			}
		}

		// Directed vs temporal tier ordering.
		for (String[] pair : directedPairs) {
			Integer parentTier = tierOf.get(pair[0]);
			Integer childTier = tierOf.get(pair[1]);
			if (parentTier == null || childTier == null) {
				continue;
			}
			if (childTier < parentTier) {
				BLogger.out.println("WARNING: Directed constraint \"" + pair[0] + "\" -> \"" + pair[1] + "\" contradicts "
						+ "temporal tier ordering (the parent is in a later tier than the child) - the directed edge "
						+ "will be created anyway, ignoring temporal order for this pair.");
			}
			else if (childTier.equals(parentTier) && prohibitSameTier) {
				BLogger.out.println("WARNING: Directed constraint \"" + pair[0] + "\" -> \"" + pair[1] + "\" connects two "
						+ "variables in the same temporal tier, which \"prohibitConnectionsSameTemporalTier\" prohibits "
						+ "- the directed edge will be created anyway.");
			}
		}

		// Undirected vs temporal tier ordering. Direction is chosen by a coin
		// flip with no temporal awareness at all (see HC*/TABU/MAHC/GES
		// initialiseVariablesANDconstraints), so any pair spanning two tiers
		// risks ending up oriented the wrong way regardless of which pair is
		// "parent"/"child" here.
		for (String[] pair : undirectedPairs) {
			Integer tierA = tierOf.get(pair[0]);
			Integer tierB = tierOf.get(pair[1]);
			if (tierA == null || tierB == null) {
				continue;
			}
			if (!tierA.equals(tierB)) {
				BLogger.out.println("WARNING: Undirected constraint \"" + pair[0] + "\" - \"" + pair[1] + "\" spans two "
						+ "different temporal tiers - its direction is chosen at random with no regard to temporal "
						+ "order, so it may end up violating the tier ordering.");
			}
			else if (prohibitSameTier) {
				BLogger.out.println("WARNING: Undirected constraint \"" + pair[0] + "\" - \"" + pair[1] + "\" connects "
						+ "two variables in the same temporal tier, which \"prohibitConnectionsSameTemporalTier\" "
						+ "prohibits - an edge will be created anyway.");
			}
		}
	}

	private List<String[]> readPairs(JSONObject jKnowledge, String key) {
		List<String[]> pairs = new ArrayList<>();
		if (!jKnowledge.has(key)) {
			return pairs;
		}
		JSONArray jArray = jKnowledge.getJSONArray(key);
		for (int i = 0; i < jArray.length(); i++) {
			JSONArray jRow = jArray.getJSONArray(i);
			pairs.add(new String[]{jRow.getString(0), jRow.getString(1)});
		}
		return pairs;
	}

	/** Order-independent key for "is this the same pair, regardless of direction". */
	private static String pairKey(String a, String b) {
		return a.compareTo(b) <= 0 ? a + " " + b : b + " " + a;
	}

	/** Order-sensitive key for "is this the same directed edge, in this specific direction". */
	private static String orderedPairKey(String from, String to) {
		return from + " -> " + to;
	}

	private void registerTempFileConditional(File file){
		TempFileCleanup.registerTempFile(file, config);
		if (!Boolean.parseBoolean(MinervaProperties.getProperty("com.agenarisk.learning.structure.deleteTransientFiles", "true"))){
			Path outPath = Paths.get(Optional.ofNullable(data.get("outPath")).orElse("."));
			String prefix = Optional.ofNullable(data.get("prefix")).map(p -> p + "_").orElse("");
			Path copyToPath = outPath.resolve(prefix + file.getName());
			BLogger.logConditional("Persisting temp file: " + copyToPath);
			try {
				Files.copy(file.toPath(), copyToPath, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException ex){
				BLogger.logConditional("Failed to copy transient files");
				BLogger.logThrowableIfDebug(ex);
			}
		}
	}
}
