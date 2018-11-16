package com.agenarisk.api.io.stub;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class NodeConfiguration {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		configuration,
		type,
		simulated,
		simulationConvergence
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum States {
		states,
		state
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Table {
		table,
		type,
		
		partitions,
		partition,
		
		expressions,
		expression,
		
		probabilities,
		row,
		column,
		cell,
		
		nptCompiled
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum TableType {
		Manual,
		Expression,
		Partitioned
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Variables {
		variables,
		variable,
		name,
		value
	}
	
}
