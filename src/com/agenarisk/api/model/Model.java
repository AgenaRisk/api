package com.agenarisk.api.model;

import com.agenarisk.api.util.Ref;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.FileIOException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.io.stub.Audit;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.Picture;
import com.agenarisk.api.io.stub.Text;
import com.agenarisk.api.model.interfaces.IDContainer;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.JSONUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.MessagePassingLinkException;
import uk.co.agena.minerva.model.PropagationException;
import uk.co.agena.minerva.model.PropagationTerminatedException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.io.FileHandlingException;

/**
 * Model class represents an AgenaRisk model that may contain a number of Bayesian networks, datasets etc, equivalent to com.agenarisk.api.model.Model in AgenaRisk Java API v1.
 * 
 * @author Eugene Dementiev
 */
public class Model implements IDContainer<ModelException>, Storable {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		model
	}
	
	/**
	 * ID-Network map of this Model
	 * This should not be directly returned to other components and should be modified only by this class in a block synchronized on IDContainer.class
	 */
	private final Map<String, Network> networks = Collections.synchronizedMap(new HashMap<>());
	
	/**
	 * ID-DataSet map of this Model
	 * This should not be directly returned to other components and should be modified only by this class in a block synchronized on IDContainer.class
	 */
	private final Map<String, DataSet> datasets = Collections.synchronizedMap(new HashMap());
	
	/**
	 * The underlying logical Model
	 */
	private final uk.co.agena.minerva.model.Model logicModel;
	
	/**
	 * Should be set on model load, and then saved on model save
	 */
	private JSONArray texts, pictures;
	private JSONObject graphics, meta, audit;
	
	/**
	 * Constructor for Model class.
	 * <br>
	 * The Model is created without DataSets or Networks.
	 * <br>
	 * To be used by Model factory method.
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
	 * Loads a Model from a JSON at the given file path.
	 * 
	 * @param path file path to JSON-encoded Model
	 * @return loaded Model
	 * @throws ModelException if failed to read the file or if JSON was corrupt or missing required attributes
	 */
	public static Model loadModel(String path) throws ModelException {
		Environment.logIfDebug("Loading model from: " + path);
		
		JSONObject json;
		try {
			json = JSONUtils.loadModelJSON(path);
		}
		catch (JSONException ex){
			throw new ModelException("Invalid model file format", ex);
		}
		
		Model model;
		try {
			model = Model.createModel(json);
		}
		catch (JSONException ex){
			throw new ModelException(JSONUtils.createMissingAttrMessage(ex));
		}
				
		Environment.logIfDebug("Model loaded");
		
		return model;
	}
	
	/**
	 * Factory method to create an empty instance of a Model class.
	 * 
	 * @return new instance of a Model
	 */
	public static Model createModel(){
		return new Model();
	}
	
	/**
	 * Factory method to create an instance of a Model from provided JSONObject.
	 * <br>
	 * Creates all member components.
	 * 
	 * @param json JSONObject representing this model, including structure, tables, graphics etc
	 * @return Model created Model
	 * @throws ModelException if JSON structure is invalid or inconsistent
	 */
	public static Model createModel(JSONObject json) throws ModelException, JSONException {
		
		Model model = createModel();
		
		JSONObject jsonModel = json.getJSONObject(Field.model.toString());
		
		// Create networks
		JSONArray jsonNetworks = jsonModel.getJSONArray(Network.Field.networks.toString());
		for(int i = 0; i < jsonNetworks.length(); i++){
			model.createNetwork(jsonNetworks.getJSONObject(i));
		}

		// Create cross Network links
		try {
			Node.linkNodes(model, jsonModel.optJSONArray(Link.Field.links.toString()));
		}
		catch (JSONException | NodeException ex){
			throw new ModelException("Failed to create Links", ex);
		}
		
		// Apply settings
		
		// Load and apply DataSets
		
		// Load Notes
		if (jsonModel.has(Meta.Field.meta.toString())){
			// Load notes
		}
		
		// Retrieve extra fields from JSON
		model.texts = jsonModel.optJSONArray(Text.Field.texts.toString());
		model.pictures = jsonModel.optJSONArray(Picture.Field.pictures.toString());
		model.audit = jsonModel.optJSONObject(Audit.Field.audit.toString());
		model.graphics = jsonModel.optJSONObject(Graphics.Field.graphics.toString());
		
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	/**
	 * Creates a Network and adds it to this Model.
	 * <br>
	 * Creates all member components.
	 * 
	 * @param json JSONObject representing the network, including structure, tables, graphics etc
	 * @return Network object
	 */
	public Network createNetwork(JSONObject json) {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	/**
	 * Creates a new empty Network and adds it to this Model.
	 * 
	 * @param id unique ID of the Network
	 * @return the Network instance added to this Model
	 * @throws ModelException if a Network with this ID already exists
	 */
	public Network createNetwork(String id) throws ModelException {
		return createNetwork(id, id);
	}
	
	/**
	 * Creates a new empty Network and adds it to this Model.
	 * 
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
	 * Creates a JSON representing this Network, ready for file storage.
	 * 
	 * @return JSONObject representing this Network
	 */
	@Override
	public JSONObject toJSONObject() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	/**
	 * @throws ModelException when invalid type requested
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public Map<String,? extends Identifiable> getIdMap(Class<? extends Identifiable> idClassType) throws ModelException {
		if (Network.class.equals(idClassType)){
			return networks;
		}
		
		if (DataSet.class.equals(idClassType)){
			return datasets;
		}
		
		throw new ModelException("Invalid class type provided: "+idClassType);
	}

	/**
	 * @throws ModelException when invoked
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwIdExistsException(String id) throws ModelException {
		throw new ModelException("Network with ID `" + id + "` already exists");
	}
	
	/**
	 * @throws ModelException when invoked
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwOldIdNullException(String id) throws ModelException {
		throw new ModelException("Can't change Network ID to `" + id + "` because the Network does not exist in this Model or old ID is null");
	}
	
	/**
	 * Looks up and returns a Network by its ID.
	 * 
	 * @param id the ID of the Network to return
	 * @return the Network identified by ID or null, if no such Network exists in this Model
	 */
	public Network getNetwork(String id){
		return networks.get(id);
	}

	/**
	 * Returns a copy of ID-Network map. Once generated, membership of this map is not maintained.
	 * 
	 * @return copy of ID-Network map
	 */
	public Map<String, Network> getNetworks() {
		return new TreeMap<>(networks);
	}

	/**
	 * Returns the underlying ExtendedBN.
	 * 
	 * @return the underlying ExtendedBN
	 */
	protected uk.co.agena.minerva.model.Model getLogicModel() {
		return logicModel;
	}
	
	/**
	 * Triggers propagation in this model for all Networks and DataSets.
	 * 
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
	 * Saves the Model to a file path specified in the old CMP format.
	 * 
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

	/**
	 * Creates a new DataSet and adds it to this Model.
	 * 
	 * @param id unique ID of the DataSet
	 * @return the DataSet instance added to this Model
	 * @throws ModelException if a DataSet with this ID already exists
	 */
	public DataSet createDataSet(String id) throws ModelException {
		synchronized (IDContainer.class){
			if (datasets.containsKey(id)){
				throw new ModelException("DataSet with id `" + id + "` already exists");
			}
			datasets.put(id, null);
		}
		
		DataSet dataset;
		
		try {
			dataset = DataSet.createDataSet(this, id);
			datasets.put(id, dataset);
		}
		catch (AgenaRiskRuntimeException ex){
			datasets.remove(id);
			throw new ModelException("Failed to add DataSet `" + id + "`", ex);
		}
		
		return dataset;
	}
		
	/**
	 * Returns a copy of ID-Network map. Once generated, membership of this map is not maintained.
	 * 
	 * @return copy of ID-Network map
	 */
	public Map<String, DataSet> getDataSets() {
		return new TreeMap<>(datasets);
	}
	
	/**
	 * Creates a CrossNetworkLink of given Type.
	 * <br>
	 * Type can not be CrossNetworkLink.Type.State, use createCrossNetworkLink(Node, Node, String) instead.
	 * 
	 * @param source source Node of the link
	 * @param target target Node of the link
	 * @param type type of the message for the link to pass
	 * 
	 * @throws ModelException if both Nodes are in the same Network; invalid Type given, or source Node already has incoming links
	 */
	public void createLink(Node source, Node target, CrossNetworkLink.Type type) throws ModelException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Creates a CrossNetworkLink of given Type.
	 * <br>
 	 * Type can not be CrossNetworkLink.Type.State, use createCrossNetworkLink(Node, Node, String) instead.
	 *
	 * @param sourceNetworkId ID of source Network of the link
	 * @param sourceNodeId ID of source Node of the link
	 * @param targetNetworkId ID of target Network of the link
	 * @param targetNodeId ID of target Node of the link
	 * @param type type of the message for the link to pass
	 * 
	 * @throws ModelException if both Nodes are in the same Network; invalid Type given, or source Node already has incoming links
	 * @throws NullPointerException if Network with this ID does not exist
	 */
	public void createLink(String sourceNetworkId, String sourceNodeId, String targetNetworkId, String targetNodeId, CrossNetworkLink.Type type) throws ModelException {
		Node source = getNetwork(sourceNetworkId).getNode(sourceNodeId);
		Node target = getNetwork(targetNetworkId).getNode(targetNodeId);
		
		createLink(source, target, type);
	}
	
	/**
	 * Creates a CrossNetworkLink of type CrossNetworkLink.Type.State that passes the given state from source Node to target Node.
	 * 
	 * @param source source Node of the link
	 * @param target target Node of the link
	 * @param state the state from source Node for the link to pass to target Node
	 * 
	 * @throws ModelException if both Nodes are in the same Network; invalid state given, or source Node already has incoming links
	 */
	public void createLink(Node source, Node target, State state) throws ModelException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Creates a CrossNetworkLink of type CrossNetworkLink.Type.State that passes the given state from source Node to target Node.
	 * 
	 * @param sourceNetworkId ID of source Network of the link
	 * @param sourceNodeId ID of source Node of the link
	 * @param targetNetworkId ID of target Network of the link
	 * @param targetNodeId ID of target Node of the link
	 * @param stateLabel the label of the state from source Node for the link to pass to target Node
	 * 
	 * @throws ModelException if both Nodes are in the same Network; invalid state given, or source Node already has incoming links
	 * @throws NullPointerException if Network with this ID does not exist
	 */
	public void createLink(String sourceNetworkId, String sourceNodeId, String targetNetworkId, String targetNodeId, String stateLabel) throws ModelException {
		Node source = getNetwork(sourceNetworkId).getNode(sourceNodeId);
		Node target = getNetwork(targetNetworkId).getNode(targetNodeId);
		
		createLink(source, target, source.getState(stateLabel));
	}
	
}
