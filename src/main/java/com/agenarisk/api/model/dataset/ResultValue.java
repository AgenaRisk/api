package com.agenarisk.api.model.dataset;

import com.agenarisk.api.model.CalculationResult;

/**
 * ResultValue represents a state or range with probability mass allocated to it.
 * 
 * @author Eugene Dementiev
 */
public class ResultValue {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		resultValues,
		resultValue,
		label,
		value
	}

	/**
	 * The CalculationResult containing the data set
	 */
	private final CalculationResult result;
	
	/**
	 * Label (state or range)
	 */
	private final String label;
	
	/**
	 * Probability mass value
	 */
	private final double value;

	/**
	 * Constructor for ResultValue.
	 * 
	 * @param result CalculationResult containing the entries
	 * @param label entry label (state or range)
	 * @param value probability mass value
	 */
	public ResultValue(CalculationResult result, String label, double value) {
		this.result = result;
		this.label = label;
		this.value = value;
	}

	/**
	 * Gets the CalculationResult.
	 * 
	 * @return the CalculationResult
	 */
	public CalculationResult getCalculationResult() {
		return result;
	}

	/**
	 * Gets the entry label.
	 * 
	 * @return the entry label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Gets the probability mass value.
	 * 
	 * @return the probability mass value
	 */
	public double getValue() {
		return value;
	}
	
}
