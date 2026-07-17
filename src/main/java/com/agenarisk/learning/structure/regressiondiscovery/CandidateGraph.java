package com.agenarisk.learning.structure.regressiondiscovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Native adjacency-by-node-id DAG representation used during {@link RegressionStructureSearch}, independent of the
 * legacy {@code BNlearning.global}/{@code Graph} (which are inseparable from that engine's static, integer-indexed
 * global state) and independent of the actual {@code api.Model} (so candidate moves can be tried/scored cheaply
 * without mutating or even touching the real model).
 *
 * @author Eugene Dementiev
 */
public class CandidateGraph {

	/**
	 * The kind of single-edge move being considered.
	 */
	public enum MoveType {
		ADD_EDGE,
		REMOVE_EDGE,
		REVERSE_EDGE
	}

	private final Set<String> nodeIds;
	private final Map<String, Set<String>> parentsByNodeId;

	public CandidateGraph(Set<String> nodeIds) {
		this.nodeIds = new TreeSet<>(nodeIds);
		this.parentsByNodeId = new HashMap<>();
		for (String id : nodeIds){
			parentsByNodeId.put(id, new HashSet<>());
		}
	}

	/**
	 * Copy constructor.
	 */
	public CandidateGraph(CandidateGraph other) {
		this.nodeIds = new TreeSet<>(other.nodeIds);
		this.parentsByNodeId = new HashMap<>();
		for (Map.Entry<String, Set<String>> entry : other.parentsByNodeId.entrySet()){
			parentsByNodeId.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
	}

	public Set<String> getNodeIds() {
		return Collections.unmodifiableSet(nodeIds);
	}

	/**
	 * @param nodeId the child node id
	 *
	 * @return parent ids of {@code nodeId}, in deterministic (sorted) order
	 */
	public List<String> getParents(String nodeId) {
		List<String> parents = new ArrayList<>(parentsByNodeId.get(nodeId));
		Collections.sort(parents);
		return parents;
	}

	public boolean hasEdge(String parentId, String childId) {
		return parentsByNodeId.get(childId).contains(parentId);
	}

	public void addEdge(String parentId, String childId) {
		parentsByNodeId.get(childId).add(parentId);
	}

	public void removeEdge(String parentId, String childId) {
		parentsByNodeId.get(childId).remove(parentId);
	}

	public void reverseEdge(String parentId, String childId) {
		removeEdge(parentId, childId);
		addEdge(childId, parentId);
	}

	/**
	 * @param parentId candidate parent id
	 * @param childId candidate child id
	 *
	 * @return true if adding a {@code parentId -> childId} edge (to a graph not already containing it) would create a
	 * cycle - a DFS from {@code parentId} following existing edges forward (child-of relationships) looking for a
	 * path back to... equivalently, DFS from {@code childId} following the "descendant" direction looking for
	 * {@code parentId}. Implemented here as: does {@code parentId} appear in the set of descendants of {@code childId}?
	 */
	public boolean wouldCreateCycle(String parentId, String childId) {
		if (parentId.equals(childId)){
			return true;
		}
		Set<String> visited = new HashSet<>();
		return isDescendant(childId, parentId, visited);
	}

	/**
	 * @return true if {@code candidateAncestorId} is reachable by following child edges (descendants) starting from
	 * {@code fromId} - i.e. whether {@code fromId} is an ancestor of {@code candidateAncestorId} in the current graph
	 */
	private boolean isDescendant(String fromId, String targetId, Set<String> visited) {
		if (!visited.add(fromId)){
			return false;
		}
		for (String nodeId : nodeIds){
			if (parentsByNodeId.get(nodeId).contains(fromId)){
				if (nodeId.equals(targetId)){
					return true;
				}
				if (isDescendant(nodeId, targetId, visited)){
					return true;
				}
			}
		}
		return false;
	}
}
