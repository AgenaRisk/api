package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enumerates all combinations of states for an ordered list of partition parent Nodes, in the same order the core
 * engine expects for {@code ExtendedNode.setPartitionedExpressions}: the first parent in the list varies slowest and
 * the last parent varies fastest (standard row-major / mixed-radix order), confirmed empirically against the engine
 * (see PartitionOrderingTest).
 *
 * @author Eugene Dementiev
 */
public class PartitionEnumerator {

	/**
	 * A single combination of parent states, as an ordered map from parent Node id to state label, iteration order
	 * matches the order of {@code partitionParents} passed to {@link #enumerate(List)}.
	 */
	public static class Combination {

		private final Map<String, String> statesByNodeId;

		private Combination(Map<String, String> statesByNodeId) {
			this.statesByNodeId = statesByNodeId;
		}

		/**
		 * Builds a Combination directly from a node-id-to-state-label map, without going through
		 * {@link #enumerate(List)}. Useful for tests, and for callers that already have a specific assignment of
		 * partition-parent states in hand.
		 *
		 * @param statesByNodeId map from partition parent node id to the state label to match
		 *
		 * @return a Combination wrapping a copy of the provided map
		 */
		public static Combination of(Map<String, String> statesByNodeId) {
			return new Combination(new LinkedHashMap<>(statesByNodeId));
		}

		public Map<String, String> getStatesByNodeId() {
			return statesByNodeId;
		}

		public String getState(String nodeId) {
			return statesByNodeId.get(nodeId);
		}

		@Override
		public String toString() {
			return statesByNodeId.toString();
		}
	}

	/**
	 * Enumerates all state combinations for the given ordered list of partition parents.
	 *
	 * @param partitionParents ordered list of parent Nodes to partition by; order matters and must match the order
	 * later passed to {@code Node.partitionByParents}/{@code Node.setTableFunctions}
	 *
	 * @return combinations in row-major order (first parent slowest, last parent fastest)
	 */
	public static List<Combination> enumerate(List<Node> partitionParents) {

		List<List<String>> statesPerParent = partitionParents.stream()
				.map(parent -> parent.getStates().stream().map(State::getLabel).collect(Collectors.toList()))
				.collect(Collectors.toList());

		List<Combination> combinations = new ArrayList<>();
		int[] indices = new int[statesPerParent.size()];
		// Product in long, then range-check: an int product silently overflows for many/high-cardinality parents,
		// which would yield a wrong (often negative → empty) combination list rather than an honest error.
		long total = 1L;
		for (List<String> states : statesPerParent){
			total *= states.size();
		}
		if (total > Integer.MAX_VALUE){
			throw new IllegalStateException("Too many parent-state combinations to enumerate (" + total
					+ "); this structure is too dense for a full table.");
		}
		int totalInt = (int) total;

		for (int combIndex = 0; combIndex < totalInt; combIndex++){
			Map<String, String> statesByNodeId = new LinkedHashMap<>();
			for (int p = 0; p < partitionParents.size(); p++){
				statesByNodeId.put(partitionParents.get(p).getId(), statesPerParent.get(p).get(indices[p]));
			}
			combinations.add(new Combination(statesByNodeId));

			// Increment mixed-radix counter, last parent fastest
			for (int p = partitionParents.size() - 1; p >= 0; p--){
				indices[p]++;
				if (indices[p] < statesPerParent.get(p).size()){
					break;
				}
				indices[p] = 0;
			}
		}

		return combinations;
	}
}
