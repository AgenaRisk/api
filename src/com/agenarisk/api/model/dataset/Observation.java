package com.agenarisk.api.model.dataset;

import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.DataSet;

/**
 * Observation class is a view of an observation, valid at the time of resolution and not maintained.
 * 
 * @author Eugene Dementiev
 */
public abstract class Observation {

	/**
	 * DataSet that contains this Observation
	 */
	private final DataSet dataset;
	
	/**
	 * Observed Node
	 */
	private final Node node;

	/**
	 * Constructor for the observation.
	 * 
	 * @param dataset DataSet that contains this Observation
	 * @param node observed Node
	 */
	public Observation(DataSet dataset, Node node) {
		this.node = node;
		this.dataset = dataset;
	}
	
	/**
	 * Returns DataSet that contains this Observation
	 * @return DataSet that contains this Observation
	 */
	public DataSet getDataSet() {
		return dataset;
	}

	/**
	 * Returns the observed Node
	 * @return the observed Node
	 */
	public Node getNode() {
		return node;
	}
	
}
