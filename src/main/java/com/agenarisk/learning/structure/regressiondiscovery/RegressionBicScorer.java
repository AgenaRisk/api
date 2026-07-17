package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import com.agenarisk.learning.structure.regression.MultinomialLogisticRegression;
import com.agenarisk.learning.structure.regression.NodeRole;
import com.agenarisk.learning.structure.regression.OrdinaryLeastSquares;
import com.agenarisk.learning.structure.regression.PartitionEnumerator;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scores one candidate parent set for one target node with a regression-based local BIC, dispatching on
 * {@link NodeRole}. Used by {@link RegressionStructureSearch} to compare candidate moves, and by the standalone
 * parameter-learning path to report fit quality for an already-fixed structure.
 * <br>
 * Deliberately does not enforce any legality/constraint checks itself (that's {@link RegressionKnowledge}'s job,
 * evaluated by the search before a candidate move is even scored) - this class only ever fits and scores whatever
 * candidate parent set it's handed.
 * <br>
 * Returns null when the candidate parent set is not scoreable given the available data (rank-deficient design
 * matrix, insufficient rows, etc.) - callers must treat a null score as an illegal/rejected move, not fall back to a
 * degraded fit (pooled/global-mean fallbacks exist for final table materialization only, not for search-time scoring
 * comparisons, so that BIC comparisons between candidate moves stay apples-to-apples).
 *
 * @author Eugene Dementiev
 */
public class RegressionBicScorer {

	private final RegressionDataset dataset;
	private final double ridgeLambda;

	public RegressionBicScorer(RegressionDataset dataset) {
		this(dataset, MultinomialLogisticRegression.DEFAULT_RIDGE_LAMBDA);
	}

	public RegressionBicScorer(RegressionDataset dataset, double ridgeLambda) {
		this.dataset = dataset;
		this.ridgeLambda = ridgeLambda;
	}

	/**
	 * Scores {@code candidateParents} as a candidate parent set for {@code target}.
	 *
	 * @param target the node being scored
	 * @param candidateParents the candidate parent set to evaluate (any mix of continuous/categorical nodes)
	 *
	 * @return the LocalScore, or null if the candidate parent set is not scoreable with the available data
	 */
	public LocalScore score(Node target, List<Node> candidateParents) {
		if (NodeRole.of(target) == NodeRole.CONTINUOUS){
			return scoreContinuousTarget(target, candidateParents);
		}
		return scoreCategoricalTarget(target, candidateParents);
	}

	private LocalScore scoreContinuousTarget(Node target, List<Node> candidateParents) {

		List<Node> continuousParents = candidateParents.stream()
				.filter(p -> NodeRole.of(p) == NodeRole.CONTINUOUS)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());
		List<Node> categoricalParents = candidateParents.stream()
				.filter(p -> NodeRole.of(p) == NodeRole.CATEGORICAL)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());

		List<String> continuousParentIds = continuousParents.stream().map(Node::getId).collect(Collectors.toList());

		List<PartitionEnumerator.Combination> combinations = categoricalParents.isEmpty()
				? java.util.Collections.singletonList((PartitionEnumerator.Combination) null)
				: PartitionEnumerator.enumerate(categoricalParents);

		List<OrdinaryLeastSquares.Result> fits = new ArrayList<>();
		for (PartitionEnumerator.Combination combination : combinations){
			RegressionDataset.Selection selection = dataset.selectRows(target.getId(), continuousParentIds, combination);
			if (selection.getN() < continuousParents.size() + 2){
				// Not enough rows for at least 1 residual degree of freedom - reject the whole candidate set rather
				// than silently falling back to a pooled/global-mean fit (search-time scoring only).
				return null;
			}
			OrdinaryLeastSquares.Result fit = OrdinaryLeastSquares.fit(selection.getX(), selection.getY());
			if (!fit.isFullRank()){
				return null;
			}
			fits.add(fit);
		}

		return ContinuousLocalScore.combine(fits);
	}

	private LocalScore scoreCategoricalTarget(Node target, List<Node> candidateParents) {

		List<Node> continuousParents = candidateParents.stream()
				.filter(p -> NodeRole.of(p) == NodeRole.CONTINUOUS)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());
		List<Node> categoricalParents = candidateParents.stream()
				.filter(p -> NodeRole.of(p) == NodeRole.CATEGORICAL)
				.sorted(Comparator.comparing(Node::getId))
				.collect(Collectors.toList());

		List<String> targetStates = statesOf(target);

		if (continuousParents.isEmpty()){
			List<String> categoricalParentIds = categoricalParents.stream().map(Node::getId).collect(Collectors.toList());
			List<List<String>> categoricalParentStates = categoricalParents.stream().map(this::statesOf).collect(Collectors.toList());

			RegressionDataset.CategoricalSelection selection = dataset.selectCategoricalRows(target.getId(), targetStates, categoricalParentIds, categoricalParentStates);
			if (selection.getN() == 0){
				return null;
			}
			MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);
			return CategoricalLocalScore.from(fit);
		}

		List<String> continuousParentIds = continuousParents.stream().map(Node::getId).collect(Collectors.toList());
		List<String> categoricalParentIds = categoricalParents.stream().map(Node::getId).collect(Collectors.toList());
		List<List<String>> categoricalParentStates = categoricalParents.stream().map(this::statesOf).collect(Collectors.toList());

		RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
				target.getId(), targetStates, continuousParentIds, categoricalParentIds, categoricalParentStates);
		if (selection.getN() == 0){
			return null;
		}
		MultinomialLogisticRegression.Result fit = MultinomialLogisticRegression.fit(selection.getX(), selection.getY(), targetStates.size(), ridgeLambda);
		return CategoricalLocalScore.from(fit);
	}

	private List<String> statesOf(Node node) {
		return node.getStates().stream().map(State::getLabel).collect(Collectors.toList());
	}
}
