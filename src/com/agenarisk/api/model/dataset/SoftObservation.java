package com.agenarisk.api.model.dataset;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.DataSet;
import java.util.Map;

/**
 * This represents a soft observation with a number of observed weighted states.
 * 
 * @author Eugene Dementiev
 */
public class SoftObservation extends Observation {
	
	/**
	 * The state-weight map
	 */
	private final Map<String, Double> weights;

	/**
	 * Constructor for this SoftObservation.
	 * 
	 * @param node observed Node
	 * @param dataset DataSet that contains this Observation
	 * @param weights state-weight map
	 */
	public SoftObservation(Node node, DataSet dataset, Map<String, Double> weights) {
		super(dataset, node);
		this.weights = weights;
	}

	/**
	 * Returns the state-weight map
	 * @return the state-weight map
	 */
	public Map<String, Double> getWeights() {
		return weights;
	}

}
