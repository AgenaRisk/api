package com.agenarisk.api.model;

import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.ScenarioException;
import com.agenarisk.api.model.interfaces.Identifiable;
import java.util.Map;
import org.apache.sling.commons.json.JSONArray;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.util.model.NameDescription;

/**
 * Scenario class represents an equivalent to a Scenario in AgenaRisk Desktop or Scenario in AgenaRisk Java API v1.
 * 
 * @author Eugene Dementiev
 */
public class Scenario implements Identifiable<ScenarioException>{

	/**
	 * Model this Scenario belongs to
	 */
	private final Model model;
	
	/**
	 * The corresponding uk.co.agena.minerva.model.scenario.Scenario
	 */
	private final uk.co.agena.minerva.model.scenario.Scenario logicScenario;

	/**
	 * Private constructor for Scenario class.
	 * <br>
	 * Should only be used by Scenario static factory methods.
	 * 
	 * @param model the Model this Scenario belongs to
	 * @param logicScenario the corresponding uk.co.agena.minerva.model.scenario.Scenario
	 */
	private Scenario(Model model, uk.co.agena.minerva.model.scenario.Scenario logicScenario) {
		this.model = model;
		this.logicScenario = logicScenario;
	}
	
	/**
	 * Factory method to create a Scenario and add it to the given Model.
	 * <br>
	 * To be used by the Model class.
	 * 
	 * @param model Model to create and add Scenario to
	 * @param id unique ID/name of the Scenario
	 * @return created Scenario
	 */
	protected static Scenario createScenario(Model model, String id){
		uk.co.agena.minerva.model.scenario.Scenario logicScenario = model.getLogicModel().addScenario(id);
		Scenario scenario = new Scenario(model, logicScenario);
		return scenario;
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
	 * Returns the Model that this Scenario belongs to.
	 * 
	 * @return the Model that this Scenario belongs to
	 */
	public final Model getModel() {
		return model;
	}

	/**
	 * Gets the ID of this Scenario.
	 * 
	 * @return the ID of this Scenario
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
	 * @throws ScenarioException if fails to change ID
	 */
	@Override
	public final void setId(String id) throws ScenarioException {
		
		try {
			getModel().changeContainedId(this, id);
		}
		catch (ModelException ex){
			throw new ScenarioException("Failed to change ID of Network `" + getId() + "`", ex);
		}
		
		getLogicScenario().setName(new NameDescription(id, id));
	}
	
	/**
	 * Sets a hard observation for a Node.
	 * 
	 * @param <T> the type of observation (expecting a String when setting a particular state or a Double when setting a numeric value)
	 * @param node the Node to set observation for
	 * @param value the observation value
	 * @throws ScenarioException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ State does not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public <T> void setObservationHard(Node node, T value) throws ScenarioException {
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
					throw new ScenarioException("State `" + value + "` does not exist in node " + node.toStringExtra(), ex);
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
	 * 
	 * @param node the Node to set observation for
	 * @param states Array of states
	 * @param weights Array of weights
	 * @throws ScenarioException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ Any of the states do not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 * <br>
	 * ∙ Unequal size of arrays
	 */
	public void setObservationSoft(Node node, String[] states, Double[] weights) throws ScenarioException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Sets a soft observation for the node, assigning a given weights to given states.
	 * <br>
	 * Note that weights will be normalised to sum up to 1.
	 * 
	 * @param node the Node to set observation for
	 * @param weights the map of states and weights
	 * @throws ScenarioException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ Any of the states do not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 */
	public void setObservationSoft(Node node, Map<String, Double> weights) throws ScenarioException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Sets observations according to the given JSON.
	 * 
	 * @param observations JSON with observations
	 * @throws ScenarioException if any of the following applies:
	 * <br>
	 * ∙ Node's Network does not belong to this Model;
	 * <br>
	 * ∙ Any of the states do not exist
	 * <br>
	 * ∙ Value passed is an invalid observation for the given Node
	 * <br>
	 * ∙ Missing or invalid attributes
	 */
	public void setObservations(JSONArray observations) throws ScenarioException {
		throw new UnsupportedOperationException("Not implemented");
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
	 * Clears all observations from this Scenario for all Networks and Nodes
	 */
	public void clearObservations() {
		throw new UnsupportedOperationException("Not implemented");
	}
}
