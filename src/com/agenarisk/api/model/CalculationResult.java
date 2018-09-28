package com.agenarisk.api.model;

import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.model.dataset.ResultEntry;
import java.util.ArrayList;
import java.util.List;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;

/**
 * CalculationResult class represents a view of the Node's calculation results.
 * <br>
 * It is valid at the time of retrieval and is not maintained.
 * 
 * @author Eugene Dementiev
 */
public class CalculationResult {

	/**
	 * DataSet to which the Result belongs to
	 */
	private final DataSet dataset;
	
	/**
	 * The Result's Node
	 */
	private final Node node;
	
	/**
	 * Whether the result is for a continuous variable
	 */
	private final boolean continuous;
	
	/**
	 * Collection of data points that make up the marginals for this Node
	 */
	private final List<ResultEntry> datapoints = new ArrayList<>();
	
	/**
	 * Constructor for the CalculationResult class.
	 * <br>
	 * Should be used by static factory methods.
	 * 
	 * @param dataset DataSet to which the CalculationResult belongs to
	 * @param node the CalculationResult's Node
	 */
	private CalculationResult(DataSet dataset, Node node){
		this.dataset = dataset;
		this.node = node;
		this.continuous = node.getLogicNode() instanceof IntegerIntervalEN || node.getLogicNode() instanceof ContinuousIntervalEN;
	}
	
	/**
	 * Creates a CalculationResult instance from JSON.
	 * <br>
	 * This should typically be used when loading marginals from file.
	 * 
	 * @param json JSONObject containing data for this CalculationResult
	 * @return constructed CalculationResult
	 * @throws DataSetException if JSON contains invalid data
	 */
	protected static CalculationResult createMarginals(JSONObject json) throws DataSetException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Creates a CalculationResult instance from MarginalDataItem in AgenaRisk Core.
	 * <br>
	 * This should typically be used when the model is calculated.
	 * 
	 * @param mdi MarginalDataItem containing data for this CalculationResult
	 * @return constructed CalculationResult
	 */
	protected static CalculationResult createMarginals(MarginalDataItem mdi) {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Returns the DataSet to which the CalculationResult belongs to.
	 * 
	 * @return the DataSet to which the CalculationResult belongs to
	 */
	public DataSet getDataSet() {
		return dataset;
	}

	/**
	 * Returns the CalculationResult's Node.
	 * 
	 * @return the CalculationResult's Node 
	 */
	public Node getNode() {
		return node;
	}

	/**
	 * Returns the data points that make up CalculationResult for this Node.
	 * 
	 * @return data points
	 */
	public List<ResultEntry> getResultEntrys() {
		return datapoints;
	}

	/**
	 * Gets entropy error value for this data set
	 * 
	 * @return entropy error value for this data set
	 */
	public double getEntropy() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Gets variance value for this data set.
	 * 
	 * @return variance value for this data set
	 */
	public double getVariance() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Gets standard deviation value for this data set.
	 * 
	 * @return standard deviation value for this data set
	 */
	public double getStandardDeviation() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Gets mean value for this data set.
	 * 
	 * @return mean value for this data set
	 */
	public double getMean() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Gets median value for this data set.
	 * 
	 * @return median value for this data set
	 */
	public double getMedian() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Gets percentile value for this data set.
	 * 
	 * @return percentile value for this data set
	 */
	public double getPercentile() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Gets lower percentile value for this data set.
	 * 
	 * @return lower percentile value for this data set
	 */
	public double getLowerPercentile() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Gets upper percentile value for this data set.
	 * 
	 * @return upper percentile value for this data set
	 */
	public double getUpperPercentile() {
		throw new UnsupportedOperationException("Not implemented");
	}
	

}
