package com.agenarisk.learning.structure.regressiondiscovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fluent, in-memory knowledge/constraints object for {@link RegressionStructureSearch} - structurally similar in
 * spirit to the legacy {@code KnowledgeConfigurer} (fluent setters, edge-level constraint vocabulary: required,
 * forbidden, forbidden-directed, temporal tiers), but implemented as plain Java predicates evaluated directly by the
 * search, with zero dependency on {@code Config}, CSV files, or the legacy {@code BNlearning} engine.
 * <br>
 * Also covers two new constraint types specific to regression-based discovery, with no legacy analog: forcing or
 * forbidding a node's "regression role" (whether it may be learned as a native regression/logit expression at all,
 * vs. staying a manual/partitioned table), and forbidding indicator/dummy-encoding of a specific parent (falling
 * back to partitioning for just that parent instead).
 *
 * @author Eugene Dementiev
 */
public class RegressionKnowledge {

	private final Set<String> requiredEdges = new HashSet<>();
	private final Set<String> forbiddenUndirectedEdges = new HashSet<>();
	private final Set<String> forbiddenDirectedEdges = new HashSet<>();
	private final List<List<String>> temporalTiers = new ArrayList<>();
	private final Map<String, Integer> tierIndexByNodeId = new HashMap<>();
	private boolean prohibitSameTierEdges = false;
	private final Set<String> forceRegressionRoleNodeIds = new HashSet<>();
	private final Set<String> forbidRegressionRoleNodeIds = new HashSet<>();
	private final Set<String> forbidIndicatorEncodingParentIds = new HashSet<>();
	private final Map<String, Integer> maxParentsOverrides = new HashMap<>();

	private static String directedKey(String parentId, String childId) {
		return parentId + "->" + childId;
	}

	private static String undirectedKey(String aId, String bId) {
		return (aId.compareTo(bId) <= 0) ? (aId + "<->" + bId) : (bId + "<->" + aId);
	}

	// --- Edge-level constraints ---

	public RegressionKnowledge requireEdge(String parentId, String childId) {
		requiredEdges.add(directedKey(parentId, childId));
		return this;
	}

	/**
	 * Forbids an edge between {@code aId} and {@code bId} in either direction.
	 */
	public RegressionKnowledge forbidEdge(String aId, String bId) {
		forbiddenUndirectedEdges.add(undirectedKey(aId, bId));
		return this;
	}

	/**
	 * Forbids only the {@code parentId -> childId} direction (the reverse remains allowed).
	 */
	public RegressionKnowledge forbidDirectedEdge(String parentId, String childId) {
		forbiddenDirectedEdges.add(directedKey(parentId, childId));
		return this;
	}

	/**
	 * Adds an ordered temporal tier: nodes in this tier may not have a parent in any later-added tier (causation
	 * only flows forward in time). Call in tier order (earliest first).
	 */
	public RegressionKnowledge addTemporalTier(List<String> nodeIdsInTier) {
		int tierIndex = temporalTiers.size();
		temporalTiers.add(new ArrayList<>(nodeIdsInTier));
		for (String nodeId : nodeIdsInTier){
			tierIndexByNodeId.put(nodeId, tierIndex);
		}
		return this;
	}

	public RegressionKnowledge setProhibitSameTierEdges(boolean enabled) {
		this.prohibitSameTierEdges = enabled;
		return this;
	}

	// --- Regression-specific constraints ---

	public RegressionKnowledge forceRegressionRole(String nodeId) {
		forceRegressionRoleNodeIds.add(nodeId);
		return this;
	}

	public RegressionKnowledge forbidRegressionRole(String nodeId) {
		forbidRegressionRoleNodeIds.add(nodeId);
		return this;
	}

	public RegressionKnowledge forbidIndicatorEncoding(String parentId) {
		forbidIndicatorEncodingParentIds.add(parentId);
		return this;
	}

	public RegressionKnowledge setMaxParents(String nodeId, int max) {
		maxParentsOverrides.put(nodeId, max);
		return this;
	}

	// --- Evaluation surface used by the search ---

	public Set<String> getRequiredEdges() {
		return Collections.unmodifiableSet(requiredEdges);
	}

	/**
	 * @return true if {@code parentId -> childId} is a required edge (must always be present)
	 */
	public boolean isEdgeRequired(String parentId, String childId) {
		return requiredEdges.contains(directedKey(parentId, childId));
	}

	/**
	 * @return true if a {@code parentId -> childId} edge is permitted at all (ignoring acyclicity, which is
	 * {@link CandidateGraph}'s concern)
	 */
	public boolean isEdgeAllowed(String parentId, String childId) {
		if (forbiddenDirectedEdges.contains(directedKey(parentId, childId))){
			return false;
		}
		if (forbiddenUndirectedEdges.contains(undirectedKey(parentId, childId))){
			return false;
		}
		return !violatesTemporalOrder(parentId, childId);
	}

	private boolean violatesTemporalOrder(String parentId, String childId) {
		Integer parentTier = tierIndexByNodeId.get(parentId);
		Integer childTier = tierIndexByNodeId.get(childId);
		if (parentTier == null || childTier == null){
			return false;
		}
		if (parentTier > childTier){
			return true;
		}
		return prohibitSameTierEdges && parentTier.intValue() == childTier.intValue();
	}

	/**
	 * @param graph the current candidate graph (before the move)
	 * @param parentId edge parent id
	 * @param childId edge child id
	 * @param type the move being considered
	 *
	 * @return true if this move is legal under both this knowledge object's constraints and acyclicity
	 */
	public boolean isMoveLegal(CandidateGraph graph, String parentId, String childId, CandidateGraph.MoveType type) {
		switch (type){
			case ADD_EDGE:
				return isEdgeAllowed(parentId, childId) && !graph.wouldCreateCycle(parentId, childId);
			case REMOVE_EDGE:
				return !isEdgeRequired(parentId, childId);
			case REVERSE_EDGE:
				if (isEdgeRequired(parentId, childId) || !isEdgeAllowed(childId, parentId)){
					return false;
				}
				CandidateGraph copy = new CandidateGraph(graph);
				copy.removeEdge(parentId, childId);
				return !copy.wouldCreateCycle(childId, parentId);
			default:
				return false;
		}
	}

	public int maxParentsFor(String nodeId, int globalDefault) {
		return maxParentsOverrides.getOrDefault(nodeId, globalDefault);
	}

	public boolean mustUseRegressionRole(String nodeId) {
		return forceRegressionRoleNodeIds.contains(nodeId);
	}

	public boolean mustNotUseRegressionRole(String nodeId) {
		return forbidRegressionRoleNodeIds.contains(nodeId);
	}

	public boolean isIndicatorEncodingForbidden(String parentId) {
		return forbidIndicatorEncodingParentIds.contains(parentId);
	}

	/**
	 * Builds a RegressionKnowledge from a JSON block. Key names mirror the legacy {@code "knowledge"} block where the
	 * concept carries over, plus new keys for the regression-specific constraints:
	 * <pre>
	 * {
	 *   "connectionsDirected": [{"parent": "A", "child": "B"}, ...],
	 *   "connectionsForbidden": [{"a": "A", "b": "B"}, ...],
	 *   "connectionsForbiddenDirected": [{"parent": "A", "child": "B"}, ...],
	 *   "connectionsTemporal": [["A","B"], ["C"], ...],
	 *   "prohibitConnectionsSameTemporalTier": true,
	 *   "forceRegressionRole": ["nodeId", ...],
	 *   "forbidRegressionRole": ["nodeId", ...],
	 *   "forbidIndicatorEncoding": ["parentId", ...],
	 *   "maxParentsOverrides": {"nodeId": 3, ...}
	 * }
	 * </pre>
	 *
	 * @param jKnowledge the knowledge JSON block
	 *
	 * @return the constructed RegressionKnowledge
	 */
	public static RegressionKnowledge fromJson(JSONObject jKnowledge) {
		RegressionKnowledge knowledge = new RegressionKnowledge();

		JSONArray jDirected = jKnowledge.optJSONArray("connectionsDirected");
		if (jDirected != null){
			for (int i = 0; i < jDirected.length(); i++){
				JSONObject edge = jDirected.getJSONObject(i);
				knowledge.requireEdge(edge.getString("parent"), edge.getString("child"));
			}
		}

		JSONArray jForbidden = jKnowledge.optJSONArray("connectionsForbidden");
		if (jForbidden != null){
			for (int i = 0; i < jForbidden.length(); i++){
				JSONObject edge = jForbidden.getJSONObject(i);
				knowledge.forbidEdge(edge.getString("a"), edge.getString("b"));
			}
		}

		JSONArray jForbiddenDirected = jKnowledge.optJSONArray("connectionsForbiddenDirected");
		if (jForbiddenDirected != null){
			for (int i = 0; i < jForbiddenDirected.length(); i++){
				JSONObject edge = jForbiddenDirected.getJSONObject(i);
				knowledge.forbidDirectedEdge(edge.getString("parent"), edge.getString("child"));
			}
		}

		JSONArray jTemporal = jKnowledge.optJSONArray("connectionsTemporal");
		if (jTemporal != null){
			for (int i = 0; i < jTemporal.length(); i++){
				JSONArray jTier = jTemporal.getJSONArray(i);
				List<String> tier = new ArrayList<>();
				for (int j = 0; j < jTier.length(); j++){
					tier.add(jTier.getString(j));
				}
				knowledge.addTemporalTier(tier);
			}
		}

		knowledge.setProhibitSameTierEdges(jKnowledge.optBoolean("prohibitConnectionsSameTemporalTier", false));

		JSONArray jForceRole = jKnowledge.optJSONArray("forceRegressionRole");
		if (jForceRole != null){
			for (int i = 0; i < jForceRole.length(); i++){
				knowledge.forceRegressionRole(jForceRole.getString(i));
			}
		}

		JSONArray jForbidRole = jKnowledge.optJSONArray("forbidRegressionRole");
		if (jForbidRole != null){
			for (int i = 0; i < jForbidRole.length(); i++){
				knowledge.forbidRegressionRole(jForbidRole.getString(i));
			}
		}

		JSONArray jForbidIndicator = jKnowledge.optJSONArray("forbidIndicatorEncoding");
		if (jForbidIndicator != null){
			for (int i = 0; i < jForbidIndicator.length(); i++){
				knowledge.forbidIndicatorEncoding(jForbidIndicator.getString(i));
			}
		}

		JSONObject jMaxParents = jKnowledge.optJSONObject("maxParentsOverrides");
		if (jMaxParents != null){
			for (String nodeId : jMaxParents.keySet()){
				knowledge.setMaxParents(nodeId, jMaxParents.getInt(nodeId));
			}
		}

		return knowledge;
	}
}
