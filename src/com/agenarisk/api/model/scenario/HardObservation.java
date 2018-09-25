package com.agenarisk.api.model.scenario;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.Scenario;

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
	 * @param scenario Scenario that contains this Observation
	 * @param value value of the observation
	 */
	public HardObservation(Node node, Scenario scenario, T value) {
		super(scenario, node);
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
