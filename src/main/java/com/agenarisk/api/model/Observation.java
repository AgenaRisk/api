package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Observation class is a view of an observation, valid at the time of resolution and not maintained.
 * 
 * @author Eugene Dementiev
 */
public class Observation<T> {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		observations,
		observation,
		network,
		node,
		constantName,
		entries,
		entry,
		value,
		weight
	}

	/**
	 * DataSet that contains this Observation
	 */
	private final DataSet dataSet;
	
	/**
	 * Observed Node
	 */
	private final Node node;
	
	/**
	 * Map of observation entries
	 * <br>
	 * Hard observations will only contain one value
	 */
	private final Map<T, Double> entries;

	/**
	 * Constructor for the observation.
	 * 
	 * @param dataSet DataSet that contains this Observation
	 * @param node observed Node
	 * @param entries Map of observation entries
	 */
	private Observation(DataSet dataSet, Node node, Map<T, Double> entries) {
		this.node = node;
		this.dataSet = dataSet;
		this.entries = new TreeMap<>(entries);
		
		if (!entries.isEmpty()){
			// Validate observation type
			
			Object keyElement = entries.keySet().toArray()[0];
			switch(keyElement.getClass().getCanonicalName()){
				case "java.lang.String":
				case "java.lang.Integer":
				case "java.lang.Double":
					break;
				default:
					throw new AgenaRiskRuntimeException("Invalid observation value type: "+keyElement.getClass().getCanonicalName());
			}
		}
		else {
			throw new AgenaRiskRuntimeException("Empty observation not allowed");
		}
	}
	
	/**
	 * Creates an observation (and the underlying logical structure).
	 * 
	 * @param <T> Integer, Double or String type of Observation
	 * @param dataSet DataSet containing the Observation
	 * @param node Node that the observation is for
	 * @param entries map of entries of values with weights
	 * 
	 * @return created Observation instance
	 */
	protected static <T> Observation<T> createObservation(DataSet dataSet, Node node, Map<T, Double> entries){
		// Create logic observation here
		return new Observation(dataSet, node, entries);
	}

	/**
	 * Returns a map of observation entries. Changes to the map are not tracked and have no effect on the model or dataset.
	 * 
	 * @return map of observation entries
	 */
	public Map<T, Double> getEntries() {
		return entries;
	}
	
	/**
	 * Returns DataSet that contains this Observation
	 * @return DataSet that contains this Observation
	 */
	public DataSet getDataSet() {
		return dataSet;
	}

	/**
	 * Returns the observed Node
	 * @return the observed Node
	 */
	public Node getNode() {
		return node;
	}
	
}
