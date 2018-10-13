package com.agenarisk.api.model;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class Settings {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		settings,
		iterations,
		convergence,
		tolerance,
		sampleSize,
		sampleSizeRanked,
		discreteTails,
		simulationLogging,
		parameterLearningLogging
	}
}
