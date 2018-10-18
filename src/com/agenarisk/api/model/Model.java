package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.exception.FileIOException;
import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.io.stub.Audit;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.NodeConfiguration;
import com.agenarisk.api.io.stub.Picture;
import com.agenarisk.api.io.stub.Text;
import com.agenarisk.api.model.interfaces.IDContainer;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.JSONUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.MessagePassingLinkException;
import uk.co.agena.minerva.model.PropagationException;
import uk.co.agena.minerva.model.PropagationTerminatedException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNNotFoundException;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.io.FileHandlingException;
import uk.co.agena.minerva.util.model.NameDescription;

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
	private JSONArray jsonTexts, jsonPictures;
	private JSONObject jsonGraphics, jsonMeta, jsonAudit;
	
	/**
	 * Constructor for Model class.
	 * <br>
	 * The Model is created with a default empty Network and a DataSet
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
			// Remove the default network and data set
			logicModel.removeExtendedBNs(logicModel.getExtendedBNAtIndex(0), true);
		}
		catch (uk.co.agena.minerva.model.ModelException ex){
			throw new AgenaRiskRuntimeException("Failed to initialise the model", ex);
		}
	}
	
	/**
	 * Loads a Model from a JSON at the given file path.
	 * 
	 * @param path file path to JSON-encoded Model
	 * 
	 * @return loaded Model
	 * 
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
			throw new ModelException("Failed to read model data", ex);
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
	 * 
	 * @return Model created Model
	 * 
	 * @throws ModelException if failed to create any of the components
	 * @throws JSONException if JSON structure is invalid or inconsistent
	 */
	public static Model createModel(JSONObject json) throws ModelException, JSONException {
		
		Model model = createModel();
		
		// Remove the default data set
		try {
			model.getLogicModel().removeScenario(model.getLogicModel().getScenarioAtIndex(0));
		}
		catch (ScenarioNotFoundException ex){
			throw new AgenaRiskRuntimeException("Failed to init an empty model", ex);
		}
		
		JSONObject jsonModel = json.getJSONObject(Field.model.toString());
		
		// Retrieve extra fields from JSON that can be used outside of this API
		model.jsonTexts = jsonModel.optJSONArray(Text.Field.texts.toString());
		model.jsonPictures = jsonModel.optJSONArray(Picture.Field.pictures.toString());
		model.jsonGraphics = jsonModel.optJSONObject(Graphics.Field.graphics.toString());
		model.jsonMeta = jsonModel.optJSONObject(Meta.Field.meta.toString());

		model.jsonAudit = jsonModel.optJSONObject(Audit.Field.audit.toString());
		
		// Load Notes
		try {
			model.loadMetaNotes();
		}
		catch (JSONException ex){
			throw new ModelException("Failed loading model notes", ex);
		}
		
		// Apply settings
		Settings.loadSettings(model, jsonModel.optJSONObject(Settings.Field.settings.toString()));
		
		// Create networks
		JSONArray jsonNetworks = jsonModel.optJSONArray(Network.Field.networks.toString());
		if (jsonNetworks == null){
			return model;
		}
			
		for(int i = 0; i < jsonNetworks.length(); i++){
			// Call public method to create a network in model from JSON
			model.createNetwork(jsonNetworks.getJSONObject(i));
		}

		// Create cross cross network links
		try {
			Node.linkNodes(model, jsonModel.optJSONArray(Link.Field.links.toString()));
		}
		catch (JSONException | LinkException | NodeException ex){
			throw new ModelException("Failed to link networks", ex);
		}
		
		List<Map.Entry<Node, Boolean>> nptStatuses = new ArrayList<>();
		// Load node tables now that all nodes, states and links had been created
		for(int i = 0; i < jsonNetworks.length(); i++){
			JSONObject jsonNetwork = jsonNetworks.getJSONObject(i);
			Network network = model.getNetwork(jsonNetwork.getString(Network.Field.id.toString()));
			
			JSONArray jsonNodes = jsonNetwork.getJSONArray(Node.Field.nodes.toString());
			if (jsonNodes != null){
				for(int j = 0; j < jsonNodes.length(); j++){
					JSONObject jsonNode = jsonNodes.getJSONObject(j);
					Node node = network.getNode(jsonNode.getString(Node.Field.id.toString()));
					
					JSONObject jsonConfiguration = jsonNode.getJSONObject(NodeConfiguration.Field.configuration.toString());
					JSONObject jsonTable = jsonConfiguration.getJSONObject(NodeConfiguration.Table.table.toString());
					try {
						node.setTable(jsonTable);
					}
					catch (NodeException ex){
						throw new ModelException("Failed to load table for node " + node, ex);
					}
					
					// Remember statuses of node NPTs
					nptStatuses.add(new java.util.AbstractMap.SimpleEntry(node, jsonTable.optBoolean(NodeConfiguration.Table.nptCompiled.toString(), false)));
				}
			}
		}
		
		// Load and apply DataSets
		JSONArray jsonDataSets = jsonModel.optJSONArray(DataSet.Field.dataSets.toString());
		if (jsonDataSets != null){
			for(int i = 0; i < jsonDataSets.length(); i++){
				try {
					model.createDataSet(jsonDataSets.getJSONObject(i));
				}
				catch (JSONException ex){
					throw new ModelException("Failed to create Network", ex);
				}
			}
		}
		
		// Load modification logs
		for(int i = 0; i < jsonNetworks.length(); i++){
			JSONObject jsonNetwork = jsonNetworks.getJSONObject(i);
			Network network = model.getNetwork(jsonNetwork.getString(Network.Field.id.toString()));
			network.getLogicNetwork().resetModificationLog();
			
			JSONArray jsonModificationLog = jsonNetwork.optJSONArray(Network.ModificationLog.modificationLog.toString());
			if (jsonModificationLog != null){
				for (int j = 0; j < jsonModificationLog.length(); j++) {
					JSONObject jsonModification = jsonModificationLog.getJSONObject(j);
					String action = jsonModification.optString(Network.ModificationLog.action.toString());
					String description = jsonModification.optString(Network.ModificationLog.description.toString());
					network.getLogicNetwork().addModificationLogItem(new NameDescription(action, description));
				}
			}
			
		}
		
		for(Map.Entry<Node, Boolean> pair: nptStatuses){
			pair.getKey().getLogicNode().setNptReCalcRequired(!pair.getValue());
		}
		
		return model;
	}
	
	/**
	 * Loads model notes from meta object.
	 * 
	 * @throws JSONException if JSON structure is invalid or inconsistent
	 */
	private void loadMetaNotes() throws JSONException{
		if (jsonMeta == null || jsonMeta.optJSONArray(Meta.Field.notes.toString()) == null){
			return;
		}
		
		JSONArray jsonNotes = jsonMeta.optJSONArray(Meta.Field.notes.toString());

		for (int i = 0; i < jsonNotes.length(); i++) {
			JSONObject jsonNote = jsonNotes.getJSONObject(i);
			String name = jsonNote.getString(Meta.Field.name.toString());
			String text = jsonNote.getString(Meta.Field.name.toString());
			getLogicModel().getNotes().addNote(name, text);
		}
		
		// Don't need to keep loaded notes in temp storage
		jsonMeta.remove(Meta.Field.notes.toString());
	}
	
	/**
	 * Creates a Network and adds it to this Model.
	 * <br>
	 * Creates all member components.
	 * <br>
	 * Note: this <b>does not</b> load node's table from JSON. Instead, use <code>node.setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param jsonNetwork JSONObject representing the network, including structure, tables, graphics etc
	 * 
	 * @return Network object
	 * 
	 * @see Node#setTable(JSONObject)
	 * 
	 * @throws ModelException
	 */
	public Network createNetwork(JSONObject jsonNetwork) throws ModelException {
		Network network;
		try {
			// Call protected method that actually creates everything in the network
			network = Network.createNetwork(this, jsonNetwork);
		}
		catch (NetworkException | JSONException ex){
			throw new ModelException("Failed to create Network", ex);
		}
		
		return network;
	}
	
	/**
	 * Creates a new empty Network and adds it to this Model.
	 * 
	 * @param id unique ID of the Network
	 * 
	 * @return the Network instance added to this Model
	 * 
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
	 * 
	 * @return the Network instance added to this Model
	 * 
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
			// Call protected factory method to create a network instance
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
	 * 
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
	 * <br>
	 * Using logic objects directly is <b>unsafe</b> and is likely to break something.
	 * 
	 * @return the underlying ExtendedBN
	 */
	public uk.co.agena.minerva.model.Model getLogicModel() {
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
	 * 
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
	 * 
	 * @return the DataSet instance added to this Model
	 * 
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
	 * Creates a DataSet for this Model from JSON data.
	 * 
	 * @param jsonDataSet the JSON data
	 * 
	 * @return created DataSet
	 * 
	 * @throws ModelException if a DataSet with this ID already exists or if JSON was corrupt or missing required attributes
	 */
	public DataSet createDataSet(JSONObject jsonDataSet) throws ModelException {
		
		DataSet dataSet;
		try {
			dataSet = DataSet.createDataSet(this, jsonDataSet);
		}
		catch (DataSetException | JSONException ex){
			throw new ModelException("Failed to add DataSet", ex);
		}

		return dataSet;
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
