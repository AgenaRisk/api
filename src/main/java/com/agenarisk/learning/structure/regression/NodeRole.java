package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Node;

/**
 * Classifies a Node's role for the purposes of OLS-based table learning.
 * <br>
 * {@code Ranked} nodes are treated as continuous regressors/targets, not categorical, because the core engine
 * models them via the same continuous/TNormal machinery as {@code ContinuousInterval}/{@code IntegerInterval} nodes
 * ({@code RankedEN extends ContinuousIntervalEN}) - so a continuous parent feeding a Ranked child is the ordinary
 * continuous-regression case, not the unsupported continuous-into-discrete case.
 *
 * @author Eugene Dementiev
 */
public enum NodeRole {

	/**
	 * Numeric node whose value can be used as an OLS regressor, or which can itself be an OLS regression target:
	 * ContinuousInterval, IntegerInterval, Ranked.
	 */
	CONTINUOUS,

	/**
	 * Node with a fixed, unordered (or non-numeric) set of states, used as a partition key rather than a regressor:
	 * Boolean, Labelled, DiscreteReal.
	 */
	CATEGORICAL;

	/**
	 * Classifies the given Node's role.
	 *
	 * @param node the Node to classify
	 *
	 * @return the Node's NodeRole
	 */
	public static NodeRole of(Node node) {
		switch (node.getType()){
			case ContinuousInterval:
			case IntegerInterval:
			case Ranked:
				return CONTINUOUS;
			case Boolean:
			case Labelled:
			case DiscreteReal:
				return CATEGORICAL;
			default:
				throw new IllegalArgumentException("Unrecognised node type: " + node.getType());
		}
	}
}
