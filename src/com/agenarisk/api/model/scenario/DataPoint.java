package com.agenarisk.api.model.scenario;

/**
 * DataPoint represents a state or dynamic range with probability mass allocated to it.
 * 
 * @author Eugene Dementiev
 */
public class DataPoint {

	/**
	 * The DataSet containing the data set
	 */
	private final DataSet dataset;
	
	/**
	 * Data point label (state or range)
	 */
	private final String label;
	
	/**
	 * Probability mass value
	 */
	private final double value;

	/**
	 * Constructor for DataPoint.
	 * 
	 * @param dataset DataSet containing the data set
	 * @param label data point label (state or range)
	 * @param value probability mass value
	 */
	public DataPoint(DataSet dataset, String label, double value) {
		this.dataset = dataset;
		this.label = label;
		this.value = value;
	}

	/**
	 * Gets the DataSet.
	 * 
	 * @return the DataSet
	 */
	public DataSet getDataSet() {
		return dataset;
	}

	/**
	 * Gets the data point label.
	 * 
	 * @return the data point label
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
