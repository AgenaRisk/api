package com.agenarisk.api.model.scenario;

/**
 * DataPointInterval represents a range with a lower and upper boundaries with a probability mass allocated to it.
 * 
 * @author Eugene Dementiev
 */
public class DataPointInterval extends DataPoint {

	/**
	 * Lower and upper boundaries of the range
	 */
	private final double lowerBound, upperBound;

	/**
	 * Constructor for DataPointInterval.
	 * 
	 * @param marginal Marginal containing the data set
	 * @param lowerBound lower bound of the range
	 * @param upperBound upper bound of the range
	 * @param value probability mass value
	 */
	public DataPointInterval(DataSet marginal, double lowerBound, double upperBound, double value) {
		super(marginal, createLabel(lowerBound, upperBound), value);
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	/**
	 * Gets lower bound of the range.
	 * 
	 * @return lower bound of the range
	 */
	public double getLowerBound() {
		return lowerBound;
	}

	/**
	 * Gets upper bound of the range.
	 * 
	 * @return upper bound of the range
	 */
	public double getUpperBound() {
		return upperBound;
	}
	
	/**
	 * Takes boundaries and creates a label such that the smaller value is on the left and the bigger is on the right.
	 * 
	 * @param lowerBound lower bound of the range
	 * @param upperBound upper bound of the range
	 * @return min and max separated by a dash that is surrounded by spaces
	 */
	private static String createLabel(double lowerBound, double upperBound){
		double min = Math.min(lowerBound, upperBound);
		double max = Math.max(lowerBound, upperBound);
		return min + " - " + max;
	}
}
