package com.agenarisk.api.model.dataset;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.DataSet;

/**
 * This represents a hard observation with a single observed value or state.
 * 
 * @author Eugene Dementiev
 */
public class HardObservation <T> extends Observation {
	
	/**
	 * The value of the observation
	 */
	private final T value;

	/**
	 * Constructor for this HardObservation.
	 * 
	 * @param node observed Node
	 * @param dataset DataSet that contains this Observation
	 * @param value value of the observation
	 */
	public HardObservation(Node node, DataSet dataset, T value) {
		super(dataset, node);
		this.value = value;
	}

	/**
	 * Returns the value of the observation
	 * @return the value of the observation
	 */
	public T getValue() {
		return value;
	}

}
