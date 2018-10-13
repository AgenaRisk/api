package com.agenarisk.api.model;

import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.model.interfaces.Identifiable;
import java.util.List;
import java.util.Map;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.model.scenario.Scenario;
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
		id
	}

	/**
	 * Model this DataSet belongs to
	 */
	private final Model model;
	
	/**
	 * The corresponding uk.co.agena.minerva.model.scenario.Scenario
	 */
	private final uk.co.agena.minerva.model.scenario.Scenario logicScenario;

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
		uk.co.agena.minerva.model.scenario.Scenario logicScenario = model.getLogicModel().addScenario(id);
		DataSet dataset = new DataSet(model, logicScenario);
		return dataset;
	}
	
	/**
	 * Creates a DataSet for the Model from JSON data.
	 * 
	 * @param model the model to create a data set in
	 * @param jsonDataSet the JSON data
	 * 
	 * @return created DataSet
	 * 
	 * @throws JSONException if JSON was corrupt or missing required attributes
	 * @throws ModelException if a DataSet with this ID already exists
	 * @throws DataSetException if failed to load observations or results data
	 */
	protected static DataSet createDataSet(Model model, JSONObject jsonDataSet) throws JSONException, ModelException, DataSetException {
		DataSet dataSet;
		try {
			String id = DataSet.Field.id.toString();
			dataSet = model.createDataSet(jsonDataSet.getString(id));
		}
		catch (JSONException ex){
			throw new ModelException("Failed reading dataset data", ex);
		}
		
		// Set observations
		if (jsonDataSet.has(Observation.Field.observations.toString())){
			try {
				JSONArray jsonObservations = jsonDataSet.getJSONArray(Observation.Field.observations.toString());
				dataSet.setObservations(jsonObservations);
			}
			catch (JSONException | DataSetException ex){
				throw new ModelException("Failed to set observations", ex);
			}
		}
		
		// Load results
		if (jsonDataSet.has(CalculationResult.Field.results.toString())){
			try {
				JSONArray jsonResults = jsonDataSet.getJSONArray(CalculationResult.Field.results.toString());
				dataSet.loadCalculationResults(jsonResults);
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
	protected final uk.co.agena.minerva.model.scenario.Scenario getLogicScenario() {
		return logicScenario;
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
	 * Sets a hard observation for a Node.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param <T> the type of observation (expecting a String when setting a particular state or a Double when setting a numeric value)
	 * @param node the Node to set observation for
	 * @param value the observation value
	 * 
	 * @throws DataSetException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ State does not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public <T> void setObservationHard(Node node, T value) throws DataSetException {
		ExtendedBN ebn = node.getNetwork().getLogicNetwork();
		ExtendedNode en = node.getLogicNode();
		
		if (en instanceof LabelledEN || en instanceof RankedEN || en instanceof DiscreteRealEN){
			// Observation is same as one of the states
			
			if (value instanceof String){
				// Find matching state
				ExtendedState state = null;
				try {
					state = en.getExtendedStateWithShortDesc((String)value);
					getLogicScenario().addHardEvidenceObservation(ebn.getId(), en.getId(), state.getId());
				}
				catch (ExtendedStateNotFoundException ex){
					throw new DataSetException("State `" + value + "` does not exist in node " + node.toStringExtra(), ex);
				}
			}
			
			if (en instanceof BooleanEN && value instanceof Boolean){
			}
			
		}
		
		throw new UnsupportedOperationException("Not implemented");
		
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
		throw new UnsupportedOperationException("Not implemented");
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
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Sets observations according to the given JSON.
	 * <br>
	 * Any existing observations are removed and replaced with this one.
	 * 
	 * @param jsonObservations JSON with observations
	 * 
	 * @throws DataSetException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ Any of the states do not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 * <br>
	 * ∙ Missing or invalid attributes
	 */
	public void setObservations(JSONArray jsonObservations) throws DataSetException {
		for (int i = 0; i < jsonObservations.length(); i++) {
			JSONObject jsonObservation = jsonObservations.optJSONObject(i);
			try {
				Observation.setObservation(model, this, jsonObservation);
			}
			catch (JSONException ex){
				throw new DataSetException("Failed to set an observation", ex);
			}
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
	 * Rebuilds CalculationResults for this DataSet from a given JSON.
	 * 
	 * @param json CalculationResults in JSON format
	 * 
	 * @throws DataSetException if JSON data is invalid
	 */
	protected void loadCalculationResults(JSONArray json) throws DataSetException {
		throw new UnsupportedOperationException("Not implemented");
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
