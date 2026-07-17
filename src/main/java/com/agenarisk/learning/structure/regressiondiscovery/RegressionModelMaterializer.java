package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds/mutates the final {@code api.Model} from a {@link RegressionStructureSearch} run: links the winning edges
 * onto the shell model's already-declared nodes (no new node creation - the shell model already declares every
 * variable's type/states/bounds; only links are unknown, since discovering them is exactly the search's job), then
 * fits and writes each node's table via {@link RegressionNodeFitter} against its now-fixed parents.
 * <br>
 * Mutates and returns the same {@code Model} instance passed in, matching the convention already used by
 * {@code RegressionTableLearningExecutor}/{@code LogisticRegressionTableLearningExecutor}.
 *
 * @author Eugene Dementiev
 */
public class RegressionModelMaterializer {

	private RegressionModelMaterializer() {
	}

	/**
	 * @param shellModel model whose first network declares every node's type/states/bounds but not necessarily the
	 * links the search discovered
	 * @param searchResult the search outcome whose winning graph is to be materialized
	 * @param fitter fits and writes each node's table once linked
	 *
	 * @return per-node fit outcomes, in the model's node order
	 *
	 * @throws Exception if linking or writing a node's table fails
	 */
	public static List<RegressionNodeFitter.NodeFitOutcome> materialize(com.agenarisk.api.model.Model shellModel, RegressionStructureResult searchResult, RegressionNodeFitter fitter) throws Exception {

		Network network = shellModel.getNetworkList().get(0);
		Map<String, Node> nodesById = new HashMap<>();
		for (Node node : network.getNodeList()){
			nodesById.put(node.getId(), node);
		}

		CandidateGraph graph = searchResult.getGraph();
		for (String nodeId : graph.getNodeIds()){
			Node child = nodesById.get(nodeId);
			if (child == null){
				continue;
			}
			for (String parentId : graph.getParents(nodeId)){
				Node parent = nodesById.get(parentId);
				if (parent != null && !child.getParents().contains(parent)){
					Node.linkNodes(parent, child);
				}
			}
		}

		List<RegressionNodeFitter.NodeFitOutcome> outcomes = new ArrayList<>();
		for (Node node : network.getNodeList()){
			outcomes.add(fitter.fitAndWrite(node));
		}

		return outcomes;
	}
}
