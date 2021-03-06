package com.agenarisk.api.model;

/**
 * ResultInterval represents a range with a lower and upper boundaries with a probability mass allocated to it.
 * 
 * @author Eugene Dementiev
 */
public class ResultInterval extends ResultValue {

	/**
	 * Lower and upper boundaries of the range
	 */
	private final double lowerBound, upperBound;

	/**
	 * Constructor for ResultInterval.
	 * 
	 * @param calculationResult Marginal containing the data set
	 * @param label entry label (state or range); if null is provided, the label will be computed from range bounds
	 * @param value probability mass value
	 * @param lowerBound lower bound of the range
	 * @param upperBound upper bound of the range
	 */
	protected ResultInterval(CalculationResult calculationResult, String label, double value, double lowerBound, double upperBound) {
		super(calculationResult, (label == null)?computeLabel(lowerBound, upperBound):label, value);
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
	private static String computeLabel(double lowerBound, double upperBound){
		double min = Math.min(lowerBound, upperBound);
		double max = Math.max(lowerBound, upperBound);
		return min + " - " + max;
	}
}
