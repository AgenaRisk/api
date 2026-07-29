package com.agenarisk.learning.structure.regressiondiscovery;

import java.util.Collections;
import java.util.Map;

/**
 * Outcome of one {@link RegressionStructureSearch#search} run: the final graph, each node's winning local score
 * (which also carries the actual fit objects needed for materialization, so the winning structure's tables can be
 * written without re-fitting), and search diagnostics.
 *
 * @author Eugene Dementiev
 */
public class RegressionStructureResult {

	private final CandidateGraph graph;
	private final Map<String, LocalScore> localScoresByNodeId;
	private final double totalBic;
	private final int iterations;
	private final boolean iterationCapReached;

	public RegressionStructureResult(CandidateGraph graph, Map<String, LocalScore> localScoresByNodeId, double totalBic, int iterations, boolean iterationCapReached) {
		this.graph = graph;
		this.localScoresByNodeId = localScoresByNodeId;
		this.totalBic = totalBic;
		this.iterations = iterations;
		this.iterationCapReached = iterationCapReached;
	}

	public CandidateGraph getGraph() {
		return graph;
	}

	public Map<String, LocalScore> getLocalScoresByNodeId() {
		return Collections.unmodifiableMap(localScoresByNodeId);
	}

	public LocalScore getLocalScore(String nodeId) {
		return localScoresByNodeId.get(nodeId);
	}

	public double getTotalBic() {
		return totalBic;
	}

	public double getTotalLogLikelihood() {
		return localScoresByNodeId.values().stream().mapToDouble(LocalScore::getLogLikelihood).sum();
	}

	public int getTotalFreeParameters() {
		return localScoresByNodeId.values().stream().mapToInt(LocalScore::getFreeParameterCount).sum();
	}

	public int getIterations() {
		return iterations;
	}

	/**
	 * @return true if the search stopped because it hit the safety iteration cap, rather than reaching a genuine
	 * local optimum (no improving move existed) - worth surfacing to the user/logs since it means the result may not
	 * be fully converged
	 */
	public boolean isIterationCapReached() {
		return iterationCapReached;
	}
}
