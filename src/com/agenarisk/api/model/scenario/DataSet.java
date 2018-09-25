package com.agenarisk.api.model.scenario;

import com.agenarisk.api.exception.ScenarioException;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.Scenario;
import java.util.ArrayList;
import java.util.List;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.MarginalDataItem;

/**
 * DataSet class represents a view of the Node's marginals.
 * <br>
 * It is valid at the time of retrieval and is not maintained.
 * 
 * @author Eugene Dementiev
 */
public class DataSet {

	/**
	 * Scenario to which the DataSet belongs to
	 */
	private final Scenario scenario;
	
	/**
	 * The DataSet's Node
	 */
	private final Node node;
	
	/**
	 * Collection of data points that make up the marginals for this Node
	 */
	private final List<DataPoint> datapoints = new ArrayList<>();
	
	/**
	 * Constructor for the DataSet class.
	 * <br>
	 * Should be used by static factory methods.
	 * 
	 * @param scenario Scenario to which the DataSet belongs to
	 * @param node the DataSet's Node
	 */
	private DataSet(Scenario scenario, Node node){
		this.scenario = scenario;
		this.node = node;
	}
	
	/**
	 * Creates a DataSet instance from JSON.
	 * <br>
	 * This should typically be used when loading marginals from file.
	 * 
	 * @param json JSONObject containing data for this DataSet
	 * @return constructed DataSet
	 * @throws ScenarioException if JSON contains invalid data
	 */
	protected static DataSet createMarginals(JSONObject json) throws ScenarioException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Creates a DataSet instance from MarginalDataItem in AgenaRisk Core.
	 * <br>
	 * This should typically be used when the model is calculated.
	 * 
	 * @param mdi MarginalDataItem containing data for this DataSet
	 * @return constructed DataSet
	 */
	protected static DataSet createMarginals(MarginalDataItem mdi) {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Returns the Scenario to which the DataSet belongs to.
	 * 
	 * @return the Scenario to which the DataSet belongs to
	 */
	public Scenario getScenario() {
		return scenario;
	}

	/**
	 * Returns the DataSet's Node.
	 * 
	 * @return the DataSet's Node 
	 */
	public Node getNode() {
		return node;
	}

	/**
	 * Returns the data points that make up DataSet for this Node.
	 * 
	 * @return data points
	 */
	public List<DataPoint> getDataPoints() {
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
