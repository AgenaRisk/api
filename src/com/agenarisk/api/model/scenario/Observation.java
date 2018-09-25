package com.agenarisk.api.model.scenario;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.Scenario;

/**
 * Observation class is a view of an observation, valid at the time of resolution and not maintained.
 * 
 * @author Eugene Dementiev
 */
public abstract class Observation {

	/**
	 * Scenario that contains this Observation
	 */
	private final Scenario scenario;
	
	/**
	 * Observed Node
	 */
	private final Node node;

	/**
	 * Constructor for the observation.
	 * 
	 * @param scenario Scenario that contains this Observation
	 * @param node observed Node
	 */
	public Observation(Scenario scenario, Node node) {
		this.node = node;
		this.scenario = scenario;
	}
	
	/**
	 * Returns Scenario that contains this Observation
	 * @return Scenario that contains this Observation
	 */
	public Scenario getScenario() {
		return scenario;
	}

	/**
	 * Returns the observed Node
	 * @return the observed Node
	 */
	public Node getNode() {
		return node;
	}
	
}
