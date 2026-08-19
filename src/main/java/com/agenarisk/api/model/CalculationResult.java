package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.io.stub.SummaryStatistic;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.Advisory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.MarginalDataItemList;
import uk.co.agena.minerva.model.MarginalDataStore;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.NumericalEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.helpers.MathsHelper;
import uk.co.agena.minerva.util.model.DataPoint;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.MinervaRangeException;

/**
 * CalculationResult class represents a view of the Node's calculation results.
 * <br>
 * It is valid at the time of retrieval and is not maintained.
 * 
 * @author Eugene Dementiev
 */
public class CalculationResult implements Storable {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		results,
		result,
		network,
		node
	}

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
	 * Collection of data points that make up the results for this Node
	 */
	private final LinkedHashMap<String, ResultValue> resultValues = new LinkedHashMap<>();
	
	private final MarginalDataItem logicResult;
	
	/**
	 * Constructor for the CalculationResult class.
	 * <br>
	 * Should be used by static factory methods.
	 * 
	 * @param dataSet DataSet to which the CalculationResult belongs to
	 * @param node the CalculationResult's Node
	 */
	private CalculationResult(DataSet dataSet, Node node) throws DataSetException{
		this.dataset = dataSet;
		this.node = node;
		this.continuous = node.getLogicNode() instanceof IntegerIntervalEN || node.getLogicNode() instanceof ContinuousIntervalEN;
		
		int scenarioIndex = dataSet.getDataSetIndex();
		ExtendedBN ebn = node.getNetwork().getLogicNetwork();
		ExtendedNode en = node.getLogicNode();
		
		MarginalDataItem mdi;
		
		try {
			MarginalDataStore mds = dataSet.getModel().getLogicModel().getMarginalDataStore();
			MarginalDataItemList mdil = mds.getMarginalDataItemListForNode(ebn, en);
			mdi = mdil.getMarginalDataItemAtIndex(scenarioIndex);
			mdi.getDataset();
		}
		catch (Exception ex){
			throw new DataSetException("No result available", ex);
		}
		
		this.logicResult = mdi;
		
		for(DataPoint dp: (List<DataPoint>)mdi.getDataset().getDataPoints()){
			double value = dp.getValue();
			String label = dp.getLabel();
			ResultValue rv;
			
			if (dp instanceof IntervalDataPoint){
				IntervalDataPoint idp = (IntervalDataPoint) dp;
				if (label.trim().isEmpty()){
					label = null;
				}
				rv = new ResultInterval(this, label, idp.getValue(), idp.getIntervalLowerBound(), idp.getIntervalUpperBound());
			}
			else {
				rv = new ResultValue(this, label, value);
			}
			
			resultValues.put(label, rv);
		}
		
	}
	
	/**
	 * Loads CalculationResult data from JSON into the underlying logic structure.
	 * <br>
	 * This should typically be used when loading results from file.
	 *
	 * @param dataSet DataSet to create the CalculationResult in
	 * @param jsonResult JSONObject containing data for this CalculationResult
	 * 
	 * @throws DataSetException if JSON contains invalid data
	 */
	protected static void loadCalculationResult(DataSet dataSet, JSONObject jsonResult) throws DataSetException {
		
		String networkId = jsonResult.getString(CalculationResult.Field.network.toString());
		String nodeId = jsonResult.getString(CalculationResult.Field.node.toString());
		Node node;
		
		try {
			node = dataSet.getModel().getNetwork(networkId).getNode(nodeId);
			node.getId(); // Trigger NPE
			
		}
		catch(NullPointerException ex){
			String message = "Not found calculation data for node with ID `"+nodeId+"` in network with ID `"+networkId+"`";
			if (Advisory.getCurrentThreadGroup() != null){
				Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage(message, ex));
				return;
			}
			else throw new DataSetException(message, ex);
		}
		
		int scenarioIndex = dataSet.getDataSetIndex();
		ExtendedBN ebn = node.getNetwork().getLogicNetwork();
		ExtendedNode en = node.getLogicNode();
		
		MarginalDataStore mds = dataSet.getModel().getLogicModel().getMarginalDataStore();
		MarginalDataItemList mdil = mds.getMarginalDataItemListForNode(ebn, en);
		if (mdil == null) {
			mdil = new MarginalDataItemList(ebn, en);
			mds.getNodeMarginalListMap().put(en, mdil);
		}
		
		while (mdil.getMarginalDataItems().size() <= scenarioIndex){
			mdil.getMarginalDataItems().add(null);
		}
		
		MarginalDataItem mdi = mdil.getMarginalDataItemAtIndex(scenarioIndex);
		if (mdi == null){
			mdi = new MarginalDataItem(dataSet.getId());
			mdi.setCallSignToUpdateOn(scenarioIndex+"");
			mdi.setOnlyUpdateOnMatchedCallSign(true);
			mdil.getMarginalDataItems().set(scenarioIndex, mdi);
		}
				
		uk.co.agena.minerva.util.model.DataSet ds = mdi.getDataset();
		ds.clearDataPoints();
		
		JSONArray jsonValues = jsonResult.getJSONArray(ResultValue.Field.resultValues.toString());
		
		for (int i = 0; i < jsonValues.length(); i++) {
			JSONObject jsonEntry = jsonValues.getJSONObject(i);
			String label = jsonEntry.getString(ResultValue.Field.label.toString());
			Double value = jsonEntry.getDouble(ResultValue.Field.value.toString());
			
			if (en instanceof NumericalEN && !(en instanceof RankedEN) && label.contains(" - ")){
				// Interval data point
				String[] bounds = label.split(" - ");
				Double lowerBound = Double.valueOf(bounds[0]);
				Double upperBound = Double.valueOf(bounds[1]);
				try {
					if (en instanceof IntegerIntervalEN){
						int ilb = lowerBound.intValue();
						int iub = upperBound.intValue();
						label = ilb + ((ilb != iub)? (" - " + iub) : "");
					}
					IntervalDataPoint idp = new IntervalDataPoint(label, value, -1, lowerBound, upperBound);
					ds.addDataPoint(idp);
				}
				catch (MinervaRangeException ex){
					throw new DataSetException("Invalid range " + label + "` in node " + node.toStringExtra(), ex);
				}
			}
			else {
				try {
					int esId = node.getLogicNode().getExtendedStateWithName(label).getId();
					DataPoint dp = new DataPoint(label, value, esId);
					ds.addDataPoint(dp);
				}
				catch (NullPointerException ex){
					ds.clearDataPoints();
					String message = "Recalculation required: result data corrupted or missing for node " + node.toStringExtra();
					
					if (Advisory.getCurrentThreadGroup() != null){
						Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage(message, ex));
						return;
					}
					throw new DataSetException(message, ex);
				}
			}

		}
		
		if (jsonResult.has(SummaryStatistic.Field.summaryStatistics.toString())){
			JSONObject jsonSS = jsonResult.getJSONObject(SummaryStatistic.Field.summaryStatistics.toString());
			double confidenceInterval = optStatistic(jsonSS, SummaryStatistic.Field.confidenceInterval.toString(), mdi.getConfidenceInterval());
			double mean = optStatistic(jsonSS, SummaryStatistic.Field.mean.toString(), mdi.getMeanValue());
			double median = optStatistic(jsonSS, SummaryStatistic.Field.median.toString(), mdi.getMedianValue());
			double standardDeviation = optStatistic(jsonSS, SummaryStatistic.Field.standardDeviation.toString(), mdi.getStandardDeviationValue());
			double variance = optStatistic(jsonSS, SummaryStatistic.Field.variance.toString(), mdi.getVarianceValue());
			double entropy = optStatistic(jsonSS, SummaryStatistic.Field.entropy.toString(), mdi.getEntropyValue());
			double percentile = optStatistic(jsonSS, SummaryStatistic.Field.percentile.toString(), mdi.getPercentileValue());
			double lowerPercentile = optStatistic(jsonSS, SummaryStatistic.Field.lowerPercentile.toString(), mdi.getLowerPercentile());
			double upperPercentile = optStatistic(jsonSS, SummaryStatistic.Field.upperPercentile.toString(), mdi.getUpperPercentile());
			
			mdi.setConfidenceInterval(confidenceInterval);
			mdi.setMeanValue(mean);
			mdi.setMedianValue(median);
			mdi.setStandardDeviationValue(standardDeviation);
			mdi.setVarianceValue(variance);
			mdi.setEntropyValue(entropy);
			mdi.setPercentileValue(percentile);
			mdi.setLowerPercentile(lowerPercentile);
			mdi.setUpperPercentile(upperPercentile);
		}
	}
	
	/**
	 * Gets a CalculationResult that reflects the status of the underlying logic structure at resolution time.
	 * <br>
	 * This should typically be used when the model is calculated.
	 * 
	 * @param dataset DataSet to which the CalculationResult belongs to
	 * @param node Node that the CalculationResult is associated with
	 * 
	 * @return immutable CalculationResult or null if a result is not available
	 */
	protected static CalculationResult getCalculationResult(DataSet dataset, Node node){
		try {
			return new CalculationResult(dataset, node);
		}
		catch (DataSetException ex){
			//Logger.printThrowableIfDebug(ex);
		}
		return null;
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
	 * Returns the Result Values that make up CalculationResult for this Node.
	 * 
	 * @return Result Values
	 */
	public List<ResultValue> getResultValues() {
		return resultValues.values().stream().collect(Collectors.toList());
	}
	
	/**
	 * Returns a specific ResultValue by its label or null.
	 * 
	 * @param label label of the Result Value to loop up
	 * 
	 * @return ResultValue identified by the provided label
	 */
	public ResultValue getResultValue(String label) {
		return resultValues.get(label);
	}
	
	/**
	 * Gets confidence interval value for this result
	 * 
	 * @return confidence interval
	 */
	public double getConfidenceInterval(){
		return logicResult.getConfidenceInterval();
	}

	/**
	 * Gets entropy error value for this result
	 * 
	 * @return entropy error value for this result
	 */
	public double getEntropy() {
		return logicResult.getEntropyValue();
	}

	/**
	 * Gets variance value for this result.
	 * 
	 * @return variance value for this result
	 */
	public double getVariance() {
		return logicResult.getVarianceValue();
	}

	/**
	 * Gets standard deviation value for this result.
	 * 
	 * @return standard deviation value for this result
	 */
	public double getStandardDeviation() {
		return logicResult.getStandardDeviationValue();
	}

	/**
	 * Gets mean value for this result.
	 * 
	 * @return mean value for this result
	 */
	public double getMean() {
		return logicResult.getMeanValue();
	}

	/**
	 * Gets median value for this result.
	 * 
	 * @return median value for this result
	 */
	public double getMedian() {
		return logicResult.getMedianValue();
	}

	/**
	 * Gets percentile value for this result.
	 * 
	 * @return percentile value for this result
	 */
	public double getPercentile() {
		return logicResult.getPercentileValue();
	}

	/**
	 * Gets lower percentile value for this result.
	 * 
	 * @return lower percentile value for this result
	 */
	public double getLowerPercentile() {
		return logicResult.getLowerPercentile();
	}

	/**
	 * Gets upper percentile value for this result.
	 * 
	 * @return upper percentile value for this result
	 */
	public double getUpperPercentile() {
		return logicResult.getUpperPercentile();
	}
	
	/**
	 * Gets a specified percentile value for this result.
	 * 
	 * @param percentile percentile to calculate
	 * 
	 * @return specified percentile value for this result
	 */
	public double getPercentile(double percentile){
		try {
			return MathsHelper.percentile(percentile, logicResult.getDataset());
		}
		catch(Exception ex){
			throw new AgenaRiskRuntimeException("Failed to retrieve percentile " + percentile, ex);
		}
	}

	/**
	 * Returns if the calculation result is for a continuous variable.
	 * 
	 * @return true if the calculation result is for a continuous variable
	 */
	public boolean isContinuous() {
		return continuous;
	}
	
	/**
	 * Returns a JSON representation of this object.
	 * 
	 * @return JSON
	 */
	@Override
	public JSONObject toJson(){
		JSONObject json = new JSONObject();
		json.put(Field.node.toString(), node.getId());
		json.put(Field.network.toString(), node.getNetwork().getId());
		json.put(ResultValue.Field.resultValues.toString(), new JSONArray(resultValues.values().stream().map(rv -> rv.toJson()).collect(Collectors.toList())));
		if (continuous){
			JSONObject ssJson = new JSONObject();
			
			putStatistic(ssJson, SummaryStatistic.Field.confidenceInterval.toString(), getConfidenceInterval());
			putStatistic(ssJson, SummaryStatistic.Field.entropy.toString(), getEntropy());
			putStatistic(ssJson, SummaryStatistic.Field.lowerPercentile.toString(), getLowerPercentile());
			putStatistic(ssJson, SummaryStatistic.Field.mean.toString(), getMean());
			putStatistic(ssJson, SummaryStatistic.Field.median.toString(), getMedian());
			putStatistic(ssJson, SummaryStatistic.Field.percentile.toString(), getPercentile());
			putStatistic(ssJson, SummaryStatistic.Field.standardDeviation.toString(), getStandardDeviation());
			putStatistic(ssJson, SummaryStatistic.Field.upperPercentile.toString(), getUpperPercentile());
			putStatistic(ssJson, SummaryStatistic.Field.variance.toString(), getVariance());

			json.put(SummaryStatistic.Field.summaryStatistics.toString(), ssJson);
		}

		return json;
	}

	/**
	 * Writes a double statistic, preserving a non-finite value as its {@code Double.toString()} form
	 * ({@code "NaN"}, {@code "Infinity"}, {@code "-Infinity"}) instead of aborting the whole result.
	 *
	 * <p>{@code JSONObject.put(String, double)} runs {@code testValidity}, which throws
	 * {@code JSONException("JSON does not allow non-finite numbers.")} for NaN or an infinity. Because
	 * every node's results are serialised into one document, a single non-finite statistic on a single
	 * node lost the entire calculation — every other node, every other network — with an error naming
	 * neither the node nor the field. The engine does not behave that way: {@code MarginalDataItem}
	 * stores whatever the maths produced and writes it to XML as {@code value+""}, so the Swing path
	 * displays the value and carries on.
	 *
	 * <p>This matches that. The string form round-trips: {@code JSONObject.getDouble} falls back to
	 * {@code Double.parseDouble} for a non-Number, and {@code Double.parseDouble("NaN")} is NaN, so
	 * {@code optDouble} on the read side in {@code loadSummaryStatistics} recovers the same value the
	 * engine produced. Nothing is silently replaced by zero or null, which would misreport a failed
	 * statistic as a real one.
	 *
	 * @param json the object to write into
	 * @param field field name
	 * @param value statistic value, finite or not
	 */
	private static void putStatistic(JSONObject json, String field, double value){
		if (Double.isFinite(value)){
			json.put(field, value);
		}
		else {
			json.put(field, Double.toString(value));
		}
	}

	/**
	 * Reads a double statistic written by {@link #putStatistic}, accepting either a JSON number or the
	 * {@code Double.toString()} form of a non-finite value.
	 *
	 * <p>{@code JSONObject.optDouble} cannot be used for this: org.json's number parser rejects
	 * {@code "NaN"} / {@code "Infinity"}, so it silently returns the supplied default and the stored
	 * value is lost. {@code Double.parseDouble} does accept them, which is why the engine's XML path
	 * round-trips ({@code MarginalDataItem} writes {@code value+""} and reads back through
	 * {@code Double.parseDouble}). This keeps the JSON path equivalent.
	 *
	 * @param json the object to read from
	 * @param field field name
	 * @param defaultValue value to use when the field is absent, null, or unparseable
	 * @return the statistic, possibly NaN or an infinity
	 */
	private static double optStatistic(JSONObject json, String field, double defaultValue){
		Object value = json.opt(field);
		if (value == null || JSONObject.NULL.equals(value)){
			return defaultValue;
		}
		if (value instanceof Number){
			return ((Number) value).doubleValue();
		}
		try {
			return Double.parseDouble(value.toString());
		}
		catch (NumberFormatException ex){
			return defaultValue;
		}
	}

	/**
	 * Returns a String value of the JSON representation of this object.
	 * 
	 * @return JSON string
	 */
	@Override
	public String toString(){
		return toJson().toString();
	}
	
	/**
	 * Returns a String value of the JSON representation of this object.
	 * 
	 * @param indentFactor Larger number indicates the depth to which new lines and tabs should be used to indent the JSON string
	 * 
	 * @return Indented JSON string
	 */
	public String toString(int indentFactor){
		return toJson().toString(indentFactor);
	}
	
	/**
	 * Returns the underlying API1 MarginalDataItem object. 
	 * 
	 * @return underlying logic result
	 * 
	 * @deprecated Will be affected by module architecture in the future and should not be used.
	 */
	public MarginalDataItem getLogicCalculationResult(){
		return logicResult;
	}
}
