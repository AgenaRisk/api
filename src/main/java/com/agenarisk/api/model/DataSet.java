package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.model.field.Id;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.Advisory;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.MarginalDataItemList;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.model.scenario.ObservationNotFoundException;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.model.scenario.ScenarioException;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.model.NameDescription;

/**
 * DataSet class represents an equivalent to a Scenario in AgenaRisk Desktop or Scenario in AgenaRisk Java API v1.
 * 
 * @author Eugene Dementiev
 */
public class DataSet implements Identifiable<DataSetException>, Storable {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		dataSets,
		dataSet,
		id,
		active,
		displayable,
		logPe
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
			
			String idOriginal;
			if (jsonDataSet.has(DataSet.Field.id.toString())){
				idOriginal = jsonDataSet.getString(DataSet.Field.id.toString());
			}
			else {
				idOriginal = "Scenario";
			}
			String id = String.valueOf(idOriginal);
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
				JSONObject jsonObservation = jsonObservations.optJSONObject(i);
				try {
					dataSet.setObservation(jsonObservation);
				}
				catch (Exception ex){
					if (Advisory.getCurrentThreadGroup() != null){
						String networkId = jsonObservation.optString(Observation.Field.network.toString());
						String nodeId = jsonObservation.optString(Observation.Field.node.toString());

						String message = "Failed loading observation";
						if (nodeId != null){
							message += " for node `" + nodeId + "`";

							if (networkId != null){
								message += " in network `" + networkId + "`";
							}
						}
						Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage(message, ex));
					}
					else {
						someFailed = true;
					}
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
		
		// Load LogPE data
		if (jsonDataSet.has(Field.logPe.toString())){
			try {
				JSONObject logPe = jsonDataSet.getJSONObject(Field.logPe.toString());
				for(String networkId: logPe.keySet()){
					Double logPeValue = logPe.getDouble(networkId);
					
					dataSet.getLogicScenario().setLogProbabilityEvidence(networkId, logPeValue);
				}
			}
			catch (Exception ex){
				Logger.logIfDebug(ex, 5);
			}
		}
		
		return dataSet;
	}
	
	/**
	 * Returns the underlying logical ExtendedBN network.
	 * 
	 * @return the underlying logical ExtendedBN network
	 * 
	 * @deprecated Will be made module-restricted in the future.
	 */
	public final Scenario getLogicScenario() {
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
			setObservation(node, value);
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
			setObservation(node, value);
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
			setObservation(node, state);
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
	protected void setObservationConstant(Node node, String constantName, double value) throws DataSetException {
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
	public void setObservation(Node node, Object value) throws DataSetException {
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
				// Validate input as a number. We don't really care double or int because direct observations ignore states
				Double.valueOf(String.valueOf(value));
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
			node.toStringExtra();
		}
		catch(NullPointerException ex){
			String message;
			Network errNet = model.getNetwork(networkId);
			if (errNet == null){
				message = "Network with ID `" + networkId + "` does not exist";
			}
			else if (errNet.getNode(nodeId) == null){
				message = "Node with ID `" + nodeId + "` does not exist in Network `" + networkId + "`";
			}
			else {
				message = "Network or node not found for network ID: " + networkId + ", node ID: " + nodeId;
			}
			throw new DataSetException(message, ex);
		}
		
		JSONArray jsonEntries = jsonObservation.getJSONArray(Observation.Field.entries.toString());
		
		Map<String, Double> entries = new LinkedHashMap<>();
		
		for (int i = 0; i < jsonEntries.length(); i++) {
			JSONObject jsonEntry = jsonEntries.getJSONObject(i);
			String value = jsonEntry.get(Observation.Field.value.toString())+"";
			Double weight = jsonEntry.getDouble(Observation.Field.weight.toString());
			
			if (jsonEntries.length() == 1){
				
				// Constant
				if (jsonObservation.has(Observation.Field.constantName.toString())){
					String constantName = jsonObservation.getString(Observation.Field.constantName.toString());
					if (node.getVariable(constantName) == null){
						throw new DataSetException("Node variable `" + constantName + "` does not exist in node " + node.toStringExtra());
					}
					setObservationConstant(node, constantName, Double.valueOf(value));
					return;
				}
				
				// Hard observation
				try {
					setObservation(node, value);
				}
				catch(Exception ex){
					throw new DataSetException("Failed to set observation for node " + node.toStringExtra(), ex);
				}
				return;
			}

			entries.put(String.valueOf(value), weight);
		}
		
		try {
			setObservationSoft(node, entries);
		}
		catch(Exception ex){
			throw new DataSetException("Failed to set observation for node " + node.toStringExtra(), ex);
		}

	}
	
	/**
	 * Checks whether the provided variable in the provided node is observed in this DataSet.
	 * 
	 * @param node the Node to check
	 * @param variableName the name of the variable
	 * 
	 * @return true if the VariableObserved exists in this DataSet
	 */
	public boolean hasVariableObservation(Node node, String variableName){
		List<uk.co.agena.minerva.model.scenario.Observation> obss = getLogicScenario().getObservations(node.getNetwork().getLogicNetwork().getId(), node.getLogicNode().getId());
		return obss.stream().anyMatch(obs -> StringUtils.equalsIgnoreCase(variableName, obs.getExpressionVariableName()));
	}
	
	/**
	 * Sets a value for the provided variable in the provided Node in this DataSet to override its default value
	 * 
	 * @param node the Node with the variable
	 * @param variableName the name of the variable
	 * @param value the value of the variable to set
	 * 
	 * @throws com.agenarisk.api.exception.DataSetException if setting the value of the variable failed
	 */
	public void setVariableObservation(Node node, String variableName, double value) throws DataSetException {
		setObservationConstant(node, variableName, value);
	}
	
	/**
	 * Returns a view of VariableObservations for the provided Node.
	 * <br>
	 * Note that this is just a view of the observation and any changes to it will not affect anything beyond this particular view.
	 * <br>
	 * To change an observation, use setVariableObservation() method.
	 * 
	 * @param node the observed Node
	 * 
	 * @return a set of VariableObservations
	 */
	public Set<VariableObservation> getVariableObservations(Node node){
		LinkedHashSet<VariableObservation> varObss = new LinkedHashSet<>();
		
		((List<uk.co.agena.minerva.model.scenario.Observation>)getLogicScenario().getObservations(node.getNetwork().getLogicNetwork().getId(), node.getLogicNode().getId()))
				.stream()
				.filter(obs -> obs.getExpressionVariableName() != null && !obs.getExpressionVariableName().isEmpty())
				.map(obs -> new VariableObservation(obs, this, node))
				.collect(Collectors.toCollection(() -> varObss));
		
		return varObss;
	}
	
	/**
	 * Returns a view of the VariableObservation for the provided Node and variable name if there is one.
	 * <br>
	 * Note that this is just a view of the observation and any changes to it will not affect anything beyond this particular view.
	 * <br>
	 * To change an observation, use setVariableObservation() method.
	 * 
	 * @param node the observed Node
	 * @param variableName the variable name
	 * 
	 * @return a VariableObservation or null
	 */
	public VariableObservation getVariableObservation(Node node, String variableName){
		return getVariableObservations(node).stream().filter(obs -> StringUtils.equalsIgnoreCase(variableName, obs.getVariableName())).findFirst().orElse(null);
	}
	
	/**
	 * Returns all VariableObservations in this DataSet for all Networks and Nodes.
	 * 
	 * @return a set of VariableObservations
	 */
	public Set<VariableObservation> getVariableObservations(){
		Set<VariableObservation> varObss = new LinkedHashSet<>();
		
		getModel().getNetworks().values().forEach(net -> {
			net.getNodes()
					.values()
					.stream()
					.forEach(node -> {
						varObss.addAll(getVariableObservations(node));
					});
		});
		return varObss;
	}
	
	/**
	 * Clears the VariableObservation of the provided name in this DataSet for the provided Node.
	 * 
	 * @param node the Node with the variable
	 * @param variableName the name of the variable
	 */
	public void clearVariableObservation(Node node, String variableName){
		VariableObservation varObs = getVariableObservation(node, variableName);
		if (varObs != null){
			getLogicScenario().removeObservation(varObs.getLogicObservation(), false);
		}
	}
	
	/**
	 * Clears all VariableObservations from this DataSet for the provided Node.
	 * 
	 * @param node Node to clear VariableObservations from
	 */
	public void clearVariableObservations(Node node){
		getVariableObservations(node).forEach(obs -> {
			getLogicScenario().removeObservation(obs.getLogicObservation(), false);
		});
	}
	
	/**
	 * Clears all VariableObservations from this DataSet for all Networks and Nodes.
	 */
	public void clearVariableObservations(){
		ArrayList<uk.co.agena.minerva.model.scenario.Observation> obss = new ArrayList<>(getLogicScenario().getObservations());
		obss.stream()
				.filter(obs -> !obs.getExpressionVariableName().isEmpty())
				.forEach(obs -> getLogicScenario().removeObservation(obs, false));
	}
	
	/**
	 * Clears Observations, VariableObservations and CalculationResults data from this DataSet.
	 */
	public void clearAllData(){
		// Clear observations and variable values
		getLogicScenario().clearAllObservations(false);
		
		int index = getDataSetIndex();
		
		((Map<ExtendedNode, MarginalDataItemList>) getModel().getLogicModel().getMarginalDataStore().getNodeMarginalListMap()).values().forEach(mdil -> {
			MarginalDataItem mdiNew = new MarginalDataItem(getId());
			mdiNew.setVisible(getLogicScenario().isReportable());
			mdiNew.setCallSignToUpdateOn(Integer.toString(getLogicScenario().getId()));
			
			if (mdil.getMarginalDataItems().size()-1 >= index){
				MarginalDataItem mdiCurrent = mdil.getMarginalDataItemAtIndex(index);
				mdiNew.setOnlyUpdateOnMatchedCallSign(mdiCurrent.isOnlyUpdateOnMatchedCallSign());
				mdiNew.setUpdateOnAllEvidenceRetracted(mdiCurrent.isUpdateOnAllEvidenceRetracted());
			}
			else {
				// Create elements up to this index
				for (int i = mdil.getMarginalDataItems().size(); i <= index; i++){
					mdil.getMarginalDataItems().add(null);
				}
			}
			
			mdil.getMarginalDataItems().set(index, mdiNew);
		});
		
	}
	
	/**
	 * Clears an observation from a Node if it exists.
	 * 
	 * @param node the Node to clear the Observation from
	 */
	public void clearObservation(Node node) {
		Observation obs = getObservation(node);
		if (obs != null){
			getLogicScenario().removeObservation(obs.getLogicObservation(), false);
		}
	}
	
	/**
	 * Clears all non-variable observations from this DataSet for all Networks and Nodes
	 */
	public void clearObservations() {
		ArrayList<uk.co.agena.minerva.model.scenario.Observation> obss = new ArrayList<>(getLogicScenario().getObservations());
		obss.stream()
				.filter(obs -> obs.getExpressionVariableName() == null || obs.getExpressionVariableName().isEmpty())
				.forEach(obs -> getLogicScenario().removeObservation(obs, false));
	}
	
	/**
	 * Checks whether the Node has an observation set.
	 * 
	 * @param node the Node to check for observations
	 * 
	 * @return true if there is an observation for the Node in this DataSet
	 */
	public boolean hasObservation(Node node){
		
		try {
			getLogicScenario().getObservation(node.getNetwork().getLogicNetwork().getId(), node.getLogicNode().getId());
		}
		catch(ObservationNotFoundException ex){
			return false;
		}
		
		return true;
	}
	
	/**
	 * Returns all Observations and VariableObservations in this DataSet for the provided Node.
	 * 
	 * @param node the observed Node
	 * 
	 * @return a set of Observations and VariableObservations
	 */
	public Set<Observation> getObservationsAndVariables(Node node){
		Set<Observation> observations = new LinkedHashSet<>();
		if (hasObservation(node)){
			observations.add(getObservation(node));
		}
		observations.addAll(getVariableObservations(node));
		return observations;
	}
	
	/**
	 * Returns all Observations and VariableObservations in this DataSet for all Networks and Nodes.
	 * 
	 * @return a set of Observations and VariableObservations
	 */
	public Set<Observation> getObservationsAndVariables(){
		Set<Observation> observations = new LinkedHashSet<>();
		observations.addAll(getObservations());
		observations.addAll(getVariableObservations());
		return observations;
	}
	
	/**
	 * Returns all observations in this DataSet for all Networks and Nodes.
	 * 
	 * @return a Set of all Observations
	 */
	public Set<Observation> getObservations(){
		Set<Observation> observations = new LinkedHashSet<>();
		getModel().getNetworks().values().forEach(net -> {
			net.getNodes().values().stream()
					.filter(node -> hasObservation(node))
					.map(node -> getObservation(node))
					.collect(Collectors.toCollection(() -> observations));
		});
		return observations;
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
		uk.co.agena.minerva.model.scenario.Observation logicObservation = null;
		try {
			logicObservation = getLogicScenario().getObservation(node.getNetwork().getLogicNetwork().getId(), node.getLogicNode().getId());
		}
		catch(ObservationNotFoundException ex){
			// Do nothing, logical observation not found
		}
		
		if (logicObservation == null){
			return null;
		}
		
		return new Observation(logicObservation, this, node);
	}
	
	/**
	 * Returns the CalculationResult for the given Node.
	 * 
	 * @param node the Node for which CalculationResult should be returned
	 * 
	 * @return CalculationResult for the given Node
	 */
	public CalculationResult getCalculationResult(Node node) {
		return CalculationResult.getCalculationResult(this, node);
	}
	
	/**
	 * Gets all CalculationResults for all Nodes for this DataSet.
	 * 
	 * @return list of all CalculationResults for all Nodes for this DataSet
	 */
	public List<CalculationResult> getCalculationResults() {
		try {
			return getModel()
					.getNetworks()
					.values()
					.stream()
					.flatMap(network -> getCalculationResults(network).values().stream())
					.collect(Collectors.toList());
		}
		catch (NullPointerException ex) {
			Logger.logIfDebug("No results in data set: " + getId());
			return Arrays.asList(new CalculationResult[0]);
		}
	}
	
	/**
	 * Maps nodes to calculation results in the given network in this data set.
	 * 
	 * @param network Network to get calculation results for
	 * 
	 * @return map of nodes to results
	 */
	public Map<Node, CalculationResult> getCalculationResults(Network network) {
		return network
				.getNodes()
				.values()
				.stream()
				.map(node -> new AbstractMap.SimpleEntry<>(node, CalculationResult.getCalculationResult(this, node)))
				.filter(entry -> entry.getValue() != null)
				.collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
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
		CalculationResult.loadCalculationResult(this, jsonResult);
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
	
	/**
	 * Creates a JSONObject representation of this DataSet
	 * 
	 * @return JSONObject equivalent of this DataSet
	 */
	@Override
	public JSONObject toJson(){
		JSONObject jsonDataSet = new JSONObject();
		
		// ID
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.id.toString(), getId());
		
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.active.toString(), getLogicScenario().isReportable());
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.displayable.toString(), getLogicScenario().isDisplayOnRiskGraphs());
		
		// Observations
		JSONArray jsonObservations = new JSONArray();
		
		getObservations().stream().forEach(obs -> {
			jsonObservations.put(obs.toJson());
		});
		
		jsonDataSet.put(com.agenarisk.api.model.Observation.Field.observations.toString(), jsonObservations);
		
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.active.toString(), logicScenario.isReportable());
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.displayable.toString(), logicScenario.isDisplayOnRiskGraphs());
		
		// Results
		JSONArray jsonResults = new JSONArray();
		getCalculationResults().stream().forEach(cr -> {
			jsonResults.put(cr.toJson());
		});
		jsonDataSet.put(com.agenarisk.api.model.CalculationResult.Field.results.toString(), jsonResults);
		
		JSONObject logPe = new JSONObject(getLogicScenario().getLogPeMap());
		jsonDataSet.put(Field.logPe.toString(), logPe);
		
		return jsonDataSet;
	}
	
	public Double getLogProbabilityOfEvidence(Network network){
		return getLogicScenario().getLogProbabilityEvidence(network.getId());
	}
	
	public Double getProbabilityOfEvidence(Network network){
		return getLogicScenario().getProbabilityEvidence(network.getId());
	}
}
