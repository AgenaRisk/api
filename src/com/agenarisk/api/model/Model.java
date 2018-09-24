package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.FileIOException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.model.interfaces.IDContainer;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.MessagePassingLinkException;
import uk.co.agena.minerva.model.PropagationException;
import uk.co.agena.minerva.model.PropagationTerminatedException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.io.FileHandlingException;

/**
 * Model class represents an AgenaRisk model that may contain a number of Bayesian networks, scenarios etc, equivalent to com.agenarisk.api.model.Model in AgenaRisk Java API v1
 * @author Eugene Dementiev
 */
public class Model implements IDContainer<ModelException>, Storable {
	
	/**
	 * ID-Network map of this Model
	 * This should not be directly returned to other components and should be modified only by this class in a block synchronized on IDContainer.class
	 */
	private final Map<String, Network> networks = Collections.synchronizedMap(new HashMap<>());
	
	/**
	 * The underlying logical Model
	 */
	private final uk.co.agena.minerva.model.Model logicModel;
	
	/**
	 * Should be set on model load, and then saved on model save
	 */
	private JSONObject graphics, texts, pictures, meta, audit;
	
	/**
	 * Constructor for Model class
	 * The Model is created without Scenarios or Networks
	 * To be used by Model factory method
	 */
	private Model(){
		
		String outputMode = "system";
		if (Environment.isGuiMode()){
			outputMode = "all";
		}
		
		try {
			logicModel = uk.co.agena.minerva.model.Model.createEmptyModel(outputMode);
			logicModel.removeExtendedBNs(logicModel.getExtendedBNList().getExtendedBNs(), true);
			logicModel.removeScenario(logicModel.getScenarioAtIndex(0));
		}
		catch (uk.co.agena.minerva.model.ModelException | ScenarioNotFoundException ex){
			throw new AgenaRiskRuntimeException("Failed to initialise the model", ex);
		}
	}
	
	/**
	 * Factory method to create an empty instance of a Model class
	 * @return new instance of a Model
	 */
	public static Model createModel(){
		return new Model();
	}
	
	/**
	 * Factory method to create an instance of a Model from provided JSONObject
	 * Creates all member components
	 * @param json JSONObject representing this model, including structure, tables, graphics etc
	 * @return Model object
	 */
	public static Model createModel(JSONObject json){
		
		// Retrieve extra fields from JSON
		
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	/**
	 * Creates a Network and adds it to this Model
	 * Creates all member components
	 * @param json JSONObject representing the network, including structure, tables, graphics etc
	 * @return Network object
	 */
	public Network createNetwork(JSONObject json) {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	/**
	 * Creates a new empty Network and adds it to this Model
	 * @param id unique ID of the Network
	 * @return the Network instance added to this Model
	 * @throws ModelException if a Network with this ID already exists
	 */
	public Network createNetwork(String id) throws ModelException {
		return createNetwork(id, id);
	}
	
	/**
	 * Creates a new empty Network and adds it to this Model
	 * @param id unique ID of the Network
	 * @param name non-unique name of the Network
	 * @return the Network instance added to this Model
	 * @throws ModelException if a Network with this ID already exists
	 */
	public Network createNetwork(String id, String name) throws ModelException {
		synchronized (IDContainer.class){
			if (networks.containsKey(id)){
				throw new ModelException("Network with id `" + id + "` already exists");
			}
			networks.put(id, null);
		}
		
		Network network;
		
		try {
			network = Network.createNetwork(this, id, name);
			networks.put(id, network);
		}
		catch (AgenaRiskRuntimeException ex){
			networks.remove(id);
			throw new ModelException("Failed to add network `" + id + "`", ex);
		}
		
		return network;
	}
	
	/**
	 * Creates a JSON representing this Network, ready for file storage
	 * @return JSONObject representing this Network
	 */
	@Override
	public JSONObject toJSONObject() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	/**
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public Map<String,? extends Identifiable> getIdMap(Class<? extends Identifiable> idClassType) throws ModelException {
		if (Network.class.equals(idClassType)){
			return networks;
		}
		
		throw new ModelException("Invalid class type provided: "+idClassType);
	}

	/**
	 * @throws com.agenarisk.api.exception.ModelException
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwIdExistsException(String id) throws ModelException {
		throw new ModelException("Network with ID `" + id + "` already exists");
	}
	
	/**
	 * @throws com.agenarisk.api.exception.ModelException
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwOldIdNullException(String id) throws ModelException {
		throw new ModelException("Can't change Network ID to `" + id + "` because the Network does not exist in this Model or old ID is null");
	}
	
	/**
	 * Looks up and returns a Network by its ID
	 * @param id the ID of the Network to return
	 * @return the Network identified by ID or null, if no such Network exists in this Model
	 */
	public Network getNetwork(String id){
		return networks.get(id);
	}

	/**
	 * Returns a copy of ID-Network map. Once generated, membership of this map is not maintained.
	 * @return copy of ID-Network map
	 */
	public Map<String, Network> getNetworks() {
		return new HashMap<>(networks);
	}

	/**
	 * Returns the underlying ExtendedBN
	 * @return the underlying ExtendedBN
	 */
	protected uk.co.agena.minerva.model.Model getLogicModel() {
		return logicModel;
	}
	
	/**
	 * Triggers propagation in this model for all Networks and Scenarios
	 * @throws CalculationException if calculation failed
	 */
	public void calculate() throws CalculationException {

		// Temp
		if (getLogicModel().getScenarioList().getScenarios().isEmpty()){
			getLogicModel().addScenario("Scenario 1");
		}
		// Temp
		
		try {
			getLogicModel().calculate();
			if (!getLogicModel().isLastPropagationSuccessful()){
				throw new CalculationException("Calculation failed");
			}
		}
		catch (ExtendedBNException | MessagePassingLinkException | PropagationException | PropagationTerminatedException ex){
			throw new CalculationException("Calculation failed", ex);
		}
	}
	
	/**
	 * Saves the Model to a file path specified in the old CMP format
	 * @param path the file path to save to
	 * @throws FileIOException if saving fails
	 */
	public void save(String path) throws FileIOException {
		try {
			getLogicModel().save(path);
		}
		catch (FileHandlingException ex){
			throw new FileIOException("Failed to save the model", ex);
		}
	}

}
