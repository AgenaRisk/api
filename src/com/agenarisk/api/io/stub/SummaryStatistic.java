package com.agenarisk.api.io.stub;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class SummaryStatistic {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		confidenceInterval,
		mean,
		median,
		standardDeviation,
		variance,
		entropy,
		percentile,
		lowerPercentile,
		upperPercentile
	}
}
