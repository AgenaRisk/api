package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.io.stub.SummaryStatistic;
import com.agenarisk.api.model.dataset.ResultValue;
import com.agenarisk.api.model.field.Id;
import com.agenarisk.api.model.interfaces.Identifiable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.MarginalDataItemList;
import uk.co.agena.minerva.model.MarginalDataStore;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.NumericalEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.model.scenario.ScenarioException;
import uk.co.agena.minerva.util.model.DataPoint;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.NameDescription;

/**
 * DataSet class represents an equivalent to a Scenario in AgenaRisk Desktop or Scenario in AgenaRisk Java API v1.
 * 
 * @author Eugene Dementiev
 */
public class DataSet implements Identifiable<DataSetException>{
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		dataSets,
		dataSet,
		id,
		active,
		displayable
	}

	/**
	 * Model this DataSet belongs to
	 */
	private final Model model;
	
	/**
	 * The corresponding uk.co.agena.minerva.model.scenario.Scenario
	 */
	private uk.co.agena.minerva.model.scenario.Scenario logicScenario;

	/**
	 * Private constructor for DataSet class.
	 * <br>
	 * Should only be used by DataSet static factory methods.
	 * 
	 * @param model the Model this DataSet belongs to
	 * @param logicScenario the corresponding uk.co.agena.minerva.model.scenario.Scenario
	 */
	private DataSet(Model model, uk.co.agena.minerva.model.scenario.Scenario logicScenario) {
		this.model = model;
		this.logicScenario = logicScenario;
	}
	
	/**
	 * Factory method to create a DataSet and add it to the given Model.
	 * <br>
	 * To be used by the Model class.
	 * 
	 * @param model Model to create and add DataSet to
	 * @param id unique ID/name of the DataSet
	 * 
	 * @return created DataSet
	 */
	protected static DataSet createDataSet(Model model, String id){
		// Have to add via meta data to make sure logic scenario shows up in ARD
		uk.co.agena.minerva.model.scenario.Scenario logicScenario = new Scenario(new NameDescription(id, id));
		model.getLogicModel().getMetaData().getRootMetaDataItem().addScenario(logicScenario, model.getLogicModel());
		
		DataSet dataset = new DataSet(model, logicScenario);
		return dataset;
	}
	
	/**
	 * Creates a DataSet for the Model from JSON data.
	 * <br>
	 * If a DataSet with the provided ID already exists, it will append a number to that ID to make it unique.
	 * <br>
	 * If an observation in DataSet fails to be set, it is ignored and results for that DataSet are not loaded.
	 * 
	 * @param model the model to create a data set in
	 * @param jsonDataSet the JSON data
	 * 
	 * @return created DataSet
	 * 
	 * @throws ModelException if a DataSet with this ID already exists
	 * @throws DataSetException if failed to load observations or results data
	 */
	protected static DataSet createDataSet(Model model, JSONObject jsonDataSet) throws ModelException, DataSetException {
		
		boolean someFailed = false;
		
		DataSet dataSet;
		try {
			// Try to use original ID. If it exists, try appending numbers
			
			String idOriginal = jsonDataSet.getString(DataSet.Field.id.toString());
			String id = idOriginal;
			int i = 1;
			while(model.getDataSets().get(id) != null){
				id = idOriginal + "_" + i++;
			}
			
			dataSet = model.createDataSet(id);
		}
		catch (JSONException ex){
			throw new ModelException("Failed reading dataset data", ex);
		}
		
		dataSet.getLogicScenario().setReportable(jsonDataSet.optBoolean(Field.active.toString(), true));
		dataSet.getLogicScenario().setDisplayOnRiskGraphs(jsonDataSet.optBoolean(Field.displayable.toString(), true));
		
		// Set observations
		if (jsonDataSet.has(Observation.Field.observations.toString())){
			JSONArray jsonObservations;
			try {
				jsonObservations = jsonDataSet.getJSONArray(Observation.Field.observations.toString());
			}
			catch(JSONException ex){
				throw new ModelException("Failed reading observation data", ex);
			}
					
			for (int i = 0; i < jsonObservations.length(); i++) {
				try {
					JSONObject jsonObservation = jsonObservations.optJSONObject(i);
					dataSet.setObservation(jsonObservation);
				}
				catch (JSONException | DataSetException ex){
					someFailed = true;
				}
			}
		}
		
		if (someFailed){
			// If some observations failed to be set, skip loading calculation data for this DataSet
			return dataSet;
		}
		
		// Load results
		if (jsonDataSet.has(CalculationResult.Field.results.toString())){
			try {
				JSONArray jsonResults = jsonDataSet.getJSONArray(CalculationResult.Field.results.toString());
				for (int i = 0; i < jsonResults.length(); i++) {
					JSONObject jsonResult = jsonResults.optJSONObject(i);
					dataSet.loadCalculationResult(jsonResult);
				}
			}
			catch (JSONException | DataSetException ex){
				throw new ModelException("Failed to read results data", ex);
			}
		}
		
		return dataSet;
	}
	
	/**
	 * Returns the underlying logical ExtendedBN network.
	 * 
	 * @return the underlying logical ExtendedBN network
	 */
	protected final Scenario getLogicScenario() {
		return logicScenario;
	}

	/**
	 * Links this Network to an underlying Minerva Network object. Should only be used while wrapping a new Model around the Minerva Model.
	 * 
	 * @param logicScenario the logical scenario
	 */
	protected void setLogicScenario(Scenario logicScenario) {
		if (!new Id(getId()).equals(new Id(logicScenario.getName().getShortDescription()))){
			throw new AgenaRiskRuntimeException("Logic scenario id mismatch: " + getId() + "," + logicScenario.getName().getShortDescription());
		}
		
		this.logicScenario = logicScenario;
	}
	
	/**
	 * Returns the Model that this DataSet belongs to.
	 * 
	 * @return the Model that this DataSet belongs to
	 */
	public final Model getModel() {
		return model;
	}

	/**
	 * Gets the ID of this DataSet.
	 * 
	 * @return the ID of this DataSet
	 */
	@Override
	public final String getId() {
		return getLogicScenario().getName().getShortDescription();
	}
	
	/**
	 * Changes the ID of this Network to the provided ID, if the new ID is not already taken.
	 * <br>
	 * Will lock IDContainer.class while doing so.
	 * 
	 * @param id the new ID
	 * 
	 * @throws DataSetException if fails to change ID
	 */
	@Override
	public final void setId(String id) throws DataSetException {
		
		try {
			getModel().changeContainedId(this, id);
		}
		catch (ModelException ex){
			throw new DataSetException("Failed to change ID of Network `" + getId() + "`", ex);
		}
		
		getLogicScenario().setName(new NameDescription(id, id));
	}
	
	/**
	 * Sets a hard integer observation for a Node.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param node the Node to set observation for
	 * @param value the observation value
	 * 
	 * @throws DataSetException if any:
	 * <br>
	 * ∙ Node's Network does not belong to this Model
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public void setObservationHard(Node node, int value) throws DataSetException {
		try {
			setObservationHardGeneric(node, value);
		}
		catch(Exception ex){
			throw new DataSetException("Failed to set observation for node " + node, ex);
		}
	}
	
	/**
	 * Sets a hard real observation for a Node.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param node the Node to set observation for
	 * @param value the observation value
	 * 
	 * @throws DataSetException if any:
	 * <br>
	 * ∙ Node's Network does not belong to this Model
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public void setObservationHard(Node node, double value) throws DataSetException {
		try {
			setObservationHardGeneric(node, value);
		}
		catch(Exception ex){
			throw new DataSetException("Failed to set observation for node " + node, ex);
		}
	}
	
	/**
	 * Sets a hard observation for a Node.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param node the Node to set observation for
	 * @param state observed state
	 * 
	 * @throws DataSetException if any:
	 * <br>
	 * ∙ Node's Network does not belong to this Model
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public void setObservationHard(Node node, String state) throws DataSetException {
		try {
			setObservationHardGeneric(node, state);
		}
		catch(Exception ex){
			throw new DataSetException("Failed to set observation for node " + node, ex);
		}
	}
	
	/**
	 * Sets a node constant observation for a Node.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param node the Node to set observation for
	 * @param constantName node constant to observe
	 * @param value the constant value
	 * 
	 * @throws DataSetException if any:
	 * <br>
	 * ∙ Node's Network does not belong to this Model
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public void setObservationConstant(Node node, String constantName, double value) throws DataSetException {
		ExtendedBN ebn = node.getNetwork().getLogicNetwork();
		ExtendedNode en = node.getLogicNode();
		if (!node.getNetwork().getModel().equals(getModel())){
			throw new DataSetException("Node and DataSet belong to different models");
		}
		
		uk.co.agena.minerva.util.model.DataSet ds = new uk.co.agena.minerva.util.model.DataSet();
		
		uk.co.agena.minerva.model.scenario.Observation observation = new uk.co.agena.minerva.model.scenario.Observation(
			ebn.getId(),
			en.getId(),
			0,
			ds,
			uk.co.agena.minerva.model.scenario.Observation.OBSERVATION_TYPE_EXPRESSION_VARIABLE,
			value+"",
			en.getConnNodeId(),
			en.getExtendedStates().size()
		);
		observation.setExpressionVariableName(constantName);
		
		getLogicScenario().addObservation(observation, false);
	}
	
	/**
	 * Sets a hard observation for a Node.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * <br>
	 * The type of observation must be:
	 * <br>
	 * ∙ String for Boolean, Labelled, Ranked and DiscreteReal nodes,
	 * <br>
	 * ∙ Integer for IntegerInterval
	 * <br>
	 * ∙ Double for ContinuousInterval
	 * 
	 * @param node the Node to set observation for
	 * @param value the observation value
	 * 
	 * @throws DataSetException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model
	 * <br>
	 * ∙ State does not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public void setObservationHardGeneric(Node node, Object value) throws DataSetException {
		ExtendedBN ebn = node.getNetwork().getLogicNetwork();
		ExtendedNode en = node.getLogicNode();
		if (!node.getNetwork().getModel().equals(getModel())){
			throw new DataSetException("Node and DataSet belong to different models");
		}
		
		if (en instanceof BooleanEN && value instanceof Boolean){
			// Convert Boolean to string
			// May try to work out what is the name of the "True" state if not using standard states
			value = String.valueOf(value);
		}
		
		if (en instanceof DiscreteRealEN && value instanceof Double){
			value = String.valueOf(value);
		}
		
		if (en instanceof DiscreteRealEN && value instanceof Integer){
			value = String.valueOf(Double.valueOf(value+""));
		}
		
		if (en instanceof LabelledEN || en instanceof RankedEN || en instanceof DiscreteRealEN){
			// Observation must be the same as one of the states
			
			if (value instanceof String){
				// Find matching state
				ExtendedState state = null;
				try {
					state = en.getExtendedStateWithShortDesc((String) value);
					getLogicScenario().addHardEvidenceObservation(ebn.getId(), en.getId(), state.getId());
				}
				catch (ExtendedStateNotFoundException ex){
					throw new DataSetException("State `" + value + "` does not exist in node " + node.toStringExtra(), ex);
				}
			}
		}
		else if (en instanceof ContinuousIntervalEN || en instanceof IntegerIntervalEN){
			try {
				if (en instanceof ContinuousIntervalEN){
					Double.valueOf(String.valueOf(value));
				}
				if (en instanceof IntegerIntervalEN){
					Integer.valueOf(String.valueOf(value));
				}
			}
			catch (NumberFormatException ex){
				throw new DataSetException("Invalid observation value - not a number", ex);
			}
			
			uk.co.agena.minerva.model.scenario.Observation obs = new uk.co.agena.minerva.model.scenario.Observation(ebn.getId(), en.getId(), -1, new uk.co.agena.minerva.util.model.DataSet(new NameDescription("", ""), en.getId()), uk.co.agena.minerva.model.scenario.Observation.OBSERVATION_TYPE_NUMERIC, String.valueOf(value));
			getLogicScenario().addObservation(obs, false);
		}
		else {
			throw new DataSetException("Unsupported observation type");
		}
	}
	
	/**
	 * Sets a soft observation for the node, assigning a given weights to given states.
	 * <br>
	 * Note that weights will be normalised to sum up to 1.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param node the Node to set observation for
	 * @param states Array of states
	 * @param weights Array of weights
	 * 
	 * @throws DataSetException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ Any of the states do not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 * <br>
	 * ∙ Unequal size of arrays
	 */
	public void setObservationSoft(Node node, String[] states, Double[] weights) throws DataSetException {
		if (states.length != weights.length){
			throw new DataSetException("Arrays length not equal");
		}
		
		// Create an iterable range to go from 0 to states.length
		// turn it into a stream
		// collect it as a map, where keys are taken from states and values from weights
		// if duplicate keys found, overwrite
		// map to use is a LinkedHashMap
		Map<String, Double> entries = IntStream.range(0, states.length).boxed().collect(Collectors.toMap(i -> states[i], i -> weights[i], (k1, k2) -> {return k2;}, LinkedHashMap::new));
		try {
			setObservationSoft(node, entries);
		}
		catch(Exception ex){
			throw new DataSetException("Failed to set observation for node " + node, ex);
		}
	}
	
	/**
	 * Sets a soft observation for the node, assigning a given weights to given states.
	 * <br>
	 * Note that weights will be normalised to sum up to 1.
	 * <br>
	 * Only the states with some weight must be provided. The rest will be assumed to have zero weight.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param node the Node to set observation for
	 * @param weights the map of states and weights
	 * 
	 * @throws DataSetException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ Any of the states do not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public void setObservationSoft(Node node, Map<String, Double> weights) throws DataSetException {
		if (!node.getNetwork().getModel().equals(getModel())){
			throw new DataSetException("Node and DataSet belong to different models");
		}
		
		int netId = node.getNetwork().getLogicNetwork().getId();
		int nodeId = node.getLogicNode().getId();
		
		double[] probabilities = new double[weights.size()];
		int[] stateIds = new int[weights.size()];
		
		int i = 0;
		for(String stateName: weights.keySet()){
			ExtendedState es = node.getLogicNode().getExtendedStateWithName(stateName);
			if (es == null){
				throw new DataSetException("State `" + stateName + "` not found");
			}
			stateIds[i] = es.getId();
			probabilities[i] = weights.get(stateName);
			i++;
		}
		
		try {
			getLogicScenario().addSoftEvidenceObservation(netId, nodeId, stateIds, probabilities);
		}
		catch(ScenarioException ex){
			throw new DataSetException("Failed to add soft observation", ex);
		}
	}
	
	/**
	 * Sets observations according to the given JSON.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param jsonObservation JSON with observations
	 * 
	 * @throws DataSetException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ Any of the states do not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 * @throws JSONException if missing or invalid attributes
	 */
	public void setObservation(JSONObject jsonObservation) throws DataSetException, JSONException {
		String networkId = jsonObservation.getString(Observation.Field.network.toString());
		String nodeId = jsonObservation.getString(Observation.Field.node.toString());
		Node node;
		
		try {
			node = model.getNetwork(networkId).getNode(nodeId);
		}
		catch(NullPointerException ex){
			throw new DataSetException("Network or node not found", ex);
		}
		
		JSONArray jsonEntries = jsonObservation.getJSONArray(Observation.Field.entries.toString());
		
		Map<String, Double> entries = new LinkedHashMap<>();
		
		for (int i = 0; i < jsonEntries.length(); i++) {
			JSONObject jsonEntry = jsonEntries.getJSONObject(i);
			String value = jsonEntry.getString(Observation.Field.value.toString());
			Double weight = jsonEntry.getDouble(Observation.Field.weight.toString());
			
			if (jsonEntries.length() == 1){
				
				// Constant
				if (jsonObservation.has(Observation.Field.constantName.toString())){
					setObservationConstant(node, jsonObservation.getString(Observation.Field.constantName.toString()), Double.valueOf(value));
					return;
				}
				
				// Hard observation
				try {
					setObservationHardGeneric(node, value);
				}
				catch(Exception ex){
					throw new DataSetException("Failed to set observation for node " + node, ex);
				}
				return;
			}

			entries.put(String.valueOf(value), weight);
		}
		
		try {
			setObservationSoft(node, entries);
		}
		catch(Exception ex){
			throw new DataSetException("Failed to set observation for node " + node, ex);
		}

	}
	
	/**
	 * Clears an observation from a Node if it exists.
	 * 
	 * @param node the Node to clear the observation from
	 */
	public void clearObservation(Node node) {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Clears all observations from this DataSet for all Networks and Nodes
	 */
	public void clearObservations() {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Checks whether the Node has an observation set.
	 * @param node the Node to check for observations
	 * 
	 * @return true if there is an observation for the Node in this DataSet
	 */
	public boolean hasObservation(Node node){
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Returns a view of the observation for the given node if there is one.
	 * <br>
	 * Note that this is just a view of the observation and any changes to it will not affect anything beyond this particular view.
	 * <br>
	 * To change an observation, use setObservation() method.
	 * 
	 * @param node the observed Node
	 * 
	 * @return null if there is no observation or either HardObservation or SoftObservation
	 */
	public Observation getObservation(Node node) {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Returns the CalculationResult for the given Node.
	 * 
	 * @param node the Node for which CalculationResult should be returned
	 * 
	 * @return CalculationResult for the given Node
	 * @throws DataSetException if there are no results in this DataSet for this Node or the Node and DataSet belong to different Models
	 */
	public CalculationResult getCalculationResult(Node node) throws DataSetException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Gets all CalculationResults for all Nodes for this DataSet.
	 * 
	 * @return list of all CalculationResults for all Nodes for this DataSet
	 */
	public List<CalculationResult> getCalculationResults() {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Loads calculation result data for this DataSet from a given JSON and creates corresponding objects in the underlying logic.
	 * 
	 * @param jsonResult CalculationResult in JSON format
	 * 
	 * @throws DataSetException if any:
	 * <br>
	 * ∙ node, network or state not found
	 * <br>
	 * ∙ range lower bound more or equal then upper bound
	 * @throws JSONException if JSON data is invalid
	 */
	public void loadCalculationResult(JSONObject jsonResult) throws DataSetException, JSONException {
		String networkId = jsonResult.getString(CalculationResult.Field.network.toString());
		String nodeId = jsonResult.getString(CalculationResult.Field.node.toString());
		Node node;
		
		try {
			node = model.getNetwork(networkId).getNode(nodeId);
		}
		catch(NullPointerException ex){
			throw new DataSetException("Network or node not found", ex);
		}
		
		int scenarioIndex = getDataSetIndex();
		ExtendedBN ebn = node.getNetwork().getLogicNetwork();
		ExtendedNode en = node.getLogicNode();
		
		MarginalDataStore mds = getModel().getLogicModel().getMarginalDataStore();
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
			mdi = new MarginalDataItem(getId());
			mdi.setCallSignToUpdateOn(scenarioIndex+"");
			mdi.setOnlyUpdateOnMatchedCallSign(true);
			mdil.getMarginalDataItems().set(scenarioIndex, mdi);
		}
		
		if (jsonResult.has(SummaryStatistic.Field.summaryStatistics.toString())){
			JSONObject jsonSS = jsonResult.getJSONObject(SummaryStatistic.Field.summaryStatistics.toString());
			double confidenceInterval = jsonSS.optDouble(SummaryStatistic.Field.confidenceInterval.toString(), mdi.getConfidenceInterval());
			double mean = jsonSS.optDouble(SummaryStatistic.Field.mean.toString(), mdi.getMeanValue());
			double median = jsonSS.optDouble(SummaryStatistic.Field.median.toString(), mdi.getMedianValue());
			double standardDeviation = jsonSS.optDouble(SummaryStatistic.Field.standardDeviation.toString(), mdi.getStandardDeviationValue());
			double variance = jsonSS.optDouble(SummaryStatistic.Field.variance.toString(), mdi.getVarianceValue());
			double entropy = jsonSS.optDouble(SummaryStatistic.Field.entropy.toString(), mdi.getEntropyValue());
			double percentile = jsonSS.optDouble(SummaryStatistic.Field.percentile.toString(), mdi.getPercentileValue());
			double lowerPercentile = jsonSS.optDouble(SummaryStatistic.Field.lowerPercentile.toString(), mdi.getLowerPercentile());
			double upperPercentile = jsonSS.optDouble(SummaryStatistic.Field.upperPercentile.toString(), mdi.getUpperPercentile());
			
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
					throw new DataSetException("Invalid range " + label, ex);
				}
			}
			else {
				try {
					int esId = node.getLogicNode().getExtendedStateWithName(label).getId();
					DataPoint dp = new DataPoint(label, value, esId);
					ds.addDataPoint(dp);
				}
				catch (NullPointerException ex){
					throw new DataSetException("State `" + label + "` not found", ex);
				}
			}

		}
	}
	
	/**
	 * Gets the index of corresponding logic scenario in the underlying logical model structure
	 * 
	 * @return data set index
	 */
	protected int getDataSetIndex(){
		String thiScenarioName = this.logicScenario.getName().getShortDescription();
		int index = 0;
		
		for(Scenario scenario: (List<Scenario>)getModel().getLogicModel().getScenarioList().getScenarios()){
			if (thiScenarioName.equalsIgnoreCase(scenario.getName().getShortDescription())){
				break;
			}
			index++;
		}
		return index;
	}
}
