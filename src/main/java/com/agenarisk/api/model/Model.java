package com.agenarisk.api.model;

import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.exception.FileIOException;
import com.agenarisk.api.exception.InconsistentEvidenceException;
import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.io.FileAdapter;
import com.agenarisk.api.io.JSONAdapter;
import com.agenarisk.api.io.XMLAdapter;
import com.agenarisk.api.io.stub.Audit;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.Picture;
import com.agenarisk.api.io.stub.RiskTable;
import com.agenarisk.api.io.stub.Text;
import com.agenarisk.api.model.field.Id;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.JSONUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.questionnaire.Answer;
import uk.co.agena.minerva.model.questionnaire.Question;
import uk.co.agena.minerva.model.questionnaire.Questionnaire;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.StreamInterceptor;
import uk.co.agena.minerva.util.binaryfactorisation.BinaryBNConverter;
import uk.co.agena.minerva.util.io.FileHandlingException;
import uk.co.agena.minerva.util.model.NameDescription;
import com.agenarisk.api.model.interfaces.IdContainer;
import com.singularsys.jep.JepException;
import java.util.Collection;
import java.util.Objects;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.Model.PropagationFlag;

/**
 * Model class represents an AgenaRisk model that may contain a number of Bayesian networks, datasets etc, equivalent to com.agenarisk.api.model.Model in AgenaRisk Java API v1.
 * 
 * @author Eugene Dementiev
 */
public class Model implements IdContainer<ModelException>, Storable {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		model
	}
	
	public static enum ExportFlag {
		/**
		 * Keep meta, audit, names, notes and descriptions of Model, Networks and Nodes
		 */
		KEEP_META,
		
		/**
		 * Keep DataSet results
		 */
		KEEP_RESULTS,
		
		/**
		 * Keep DataSet observations
		 */
		KEEP_OBSERVATIONS,
		
		/**
		 * Keep Risk Table
		 */
		KEEP_RISK_TABLE,
		
		/**
		 * Keep Graphics
		 */
		KEEP_GRAPHICS,
		
		/**
		 * Keep tables for simulation nodes
		 */
		KEEP_TABLES,
		
		/**
		 * Will copy the first DataSet with observations only to the root of the JSON structure so it can be submitted to AgenaRisk Cloud
		 */
		CLOUD_DATASET
	}
	
	public static enum CalculationFlag {
		
		/**
		 * Calculate ancestors of the networks provided as well
		 */
		WITH_ANCESTORS,
		
		/**
		 * While propagating simulation nodes, keep tails and zero mass regions (default is to prune and compact)
		 */
		KEEP_TAILS_ZERO_REGIONS
	}
	
	/**
	 * ID-Network map of this Model
	 * This should not be directly returned to other components and should be modified only by this class in a block synchronized on IDContainer.class
	 */
	private final Map<Id, Network> networks = Collections.synchronizedMap(new LinkedHashMap<>());
	
	/**
	 * ID-DataSet map of this Model
	 * This should not be directly returned to other components and should be modified only by this class in a block synchronized on IDContainer.class
	 */
	private final Map<Id, DataSet> dataSets = Collections.synchronizedMap(new LinkedHashMap<>());
	
	/**
	 * The underlying logical Model
	 */
	private uk.co.agena.minerva.model.Model logicModel;
	
	/**
	 * Should be set on model load, and then saved on model save
	 */
	private JSONArray jsonTexts, jsonPictures;
	private JSONObject jsonGraphics, jsonMeta, jsonAudit;
	
	/**
	 * Constructor for Model class.
	 * <br>
	 * Any default logic Network and DataSet are removed
	 * <br>
	 * To be used by Model factory method.
	 */
	private Model(){
		initLogicModel();
	}
	
	/**
	 * Constructor for Model class.
	 * <br>
	 * The Model is linked with the provided logical model. Deep structure not created here.
	 * <br>
	 * To be used by Model factory method.
	 * 
	 * @param api1Model the logical model to link to
	 */
	private Model(uk.co.agena.minerva.model.Model api1Model){
		logicModel = api1Model;
	}
	
	/**
	 * Loads a Model from provided path
	 * 
	 * @param path path to model file
	 * 
	 * @return loaded Model
	 * 
	 * @throws ModelException if failed to read the file; or if JSON was corrupt or missing required attributes; or if CMP could not be converted to JSON
	 */
	public static Model loadModel(String path) throws ModelException {
		Logger.logIfDebug("Loading model from: " + path);
		
		if (path.matches("^\".*\"$")){
			// Strip surrounding quotes
			path = path.substring(1, path.length()-1);
		}
		
		Model model = null;
		
		Path filePath = Paths.get(path);
		if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)){
			throw new ModelException("File does not exist or is not readable: " + filePath.toAbsolutePath());
		}
		
		if (path.toLowerCase().endsWith(".cmp") || path.toLowerCase().endsWith(".ast")){
			try {
				model = Model.createModel(uk.co.agena.minerva.model.Model.load(path, uk.co.agena.minerva.model.Model.suppressMessages));
			}
			catch (Exception ex){
				throw new ModelException("Failed to convert CMP model data", ex);
			}
		}
		else {
			try {
				JSONObject json = FileAdapter.extractJSONObject(path);
				model = Model.createModel(json);
			}
			catch (AdapterException ex){
				throw new ModelException("Model data is malformed or inacessible", ex);
			}
			catch (JSONException ex){
				throw new ModelException("Model data is invalid", ex);
			}
		}
		
		if (model == null){
			throw new ModelException("Failed to load model from path");
		}
		
		if (model.getDataSets().isEmpty()){
			Logger.logIfDebug("Model has no DataSets, adding one automatically");
			model.createDataSet("Scenario 1");
		}
	
		Logger.logIfDebug("Model loaded");
		
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
	 * Creates a Model from a Minerva Model object.
	 * 
	 * @param api1Model the logical Minerva Model
	 * 
	 * @return Model object with the provided Minerva Model underneath
	 * 
	 * @throws ModelException if Minerva Model fails to be converted to JSON
	 */
	public static Model createModel(uk.co.agena.minerva.model.Model api1Model) throws ModelException {
		JSONObject json;
		Model model;
		
		try {
			json = JSONAdapter.toJSONObject(api1Model);
			model = Model.createModel(json);
		}
		catch (AdapterException ex){
			throw new ModelException("Failed to convert minerva model to JSON", ex);
		}
		catch (JSONException ex){
			throw new ModelException("Failed to convert minerva model to JSON: " + JSONUtils.createMissingAttrMessage(ex), ex);
		}
		
		model.setLogicModel(api1Model);
		
		model.getNetworks().forEach((netId, network) -> {
			network.setLogicNetwork(model.getLogicModel().getExtendedBNList().getExtendedBNWithConnID(netId));
			
			network.getNodes().forEach((nodeId, node) -> {
				node.setLogicNode(network.getLogicNetwork().getExtendedNodeWithUniqueIdentifier(nodeId));
			});
		});
		
		model.getDataSets().forEach((dsId, ds) -> {
			ds.setLogicScenario(model.getLogicModel().getScenarioWithName(dsId));
		});
		
		return model;
	}
	
	/**
	 * Factory method to create an instance of a Model from provided JSONObject.
	 * <br>
	 * Creates all member components.
	 * <br>
	 * If a DataSet with the provided ID already exists, it will append a number to that ID to make it unique.
	 * <br>
	 * If an observation in DataSet fails to be set, it is ignored and results for that DataSet are not loaded.
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
		model.absorb(json);
		return model;
	}
	
	/**
	 * Creates all the Model structure from the provided JSONObject.
	 * <br>
	 * Creates all member components.
	 * <br>
	 * If a DataSet with the provided ID already exists, it will append a number to that ID to make it unique.
	 * <br>
	 * If an observation in DataSet fails to be set, it is ignored and results for that DataSet are not loaded.
	 * 
	 * @param json JSONObject representing this model, including structure, tables, graphics etc
	 * 
	 * @throws ModelException if failed to create any of the components
	 * @throws JSONException if JSON structure is invalid or inconsistent
	 */
	protected void absorb(JSONObject json) throws ModelException, JSONException {
		
		Model model = this;
		
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
			return;
		}
			
		for(int i = 0; i < jsonNetworks.length(); i++){
			// Call public method to create a network in model from JSON
			model.createNetwork(jsonNetworks.getJSONObject(i), false);
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
					
					JSONObject jsonConfiguration = jsonNode.optJSONObject(NodeConfiguration.Field.configuration.toString());
					
					if (!node.isSimulated() && !node.isConnectedInput()){
						node.setTableUniform();
					}
					
					if (jsonConfiguration == null){
						continue;
					}
					
					JSONObject jsonTable = jsonConfiguration.optJSONObject(NodeConfiguration.Table.table.toString());
					try {
						if (node.getLogicNode().isConnectableInputNode() && !node.getLinksIn().isEmpty()){
							// Skip table
						}
						else {
							node.setTable(jsonTable);
						}
					}
					catch (NodeException ex){
						throw new ModelException("Failed to load table for node " + node, ex);
					}
					
					// Remember statuses of node NPTs
					boolean nptCompiled = false;
					try {
						nptCompiled = jsonTable.optBoolean(NodeConfiguration.Table.nptCompiled.toString(), false);
					}
					catch (NullPointerException ex){
						// Ignore
						Logger.logIfDebug("Table missing, defaulting npt compiled to false");
					}
					
					nptStatuses.add(new java.util.AbstractMap.SimpleEntry(node, nptCompiled));
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
		
		// Load Risk Table
		JSONArray jsonRiskTable = jsonModel.optJSONArray(RiskTable.Field.riskTable.toString());
		if (jsonRiskTable != null){
			model.loadRiskTable(jsonRiskTable);
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
			uk.co.agena.minerva.util.model.ModificationLog mods = pair.getKey().getNetwork().getLogicNetwork().getModificationLog();
			boolean nptReCalcRequired = !pair.getValue();
			
			if ((mods == null || mods.getModificationItems().isEmpty()) && nptReCalcRequired){
				pair.getKey().getNetwork().getLogicNetwork().addModificationLogItem(new NameDescription("Network loaded", "Network loaded"));
			}
			pair.getKey().getLogicNode().setNptReCalcRequired(nptReCalcRequired);
		}
		
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
			String text = jsonNote.getString(Meta.Field.text.toString());
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
	 * Note: loading node tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param jsonNetwork JSONObject representing the network, including structure, tables, graphics etc
	 * 
	 * @return Network object
	 * 
	 * @see Node#setTable(JSONObject)
	 * 
	 * @throws NetworkException if failed to create the Network
	 */
	public Network createNetwork(JSONObject jsonNetwork) throws NetworkException {
		return createNetwork(jsonNetwork, true);
	}
	
	/**
	 * Creates a Network and adds it to this Model.
	 * <br>
	 * Creates all member components.
	 * <br>
	 * Note: loading node tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param jsonNetwork JSONObject representing the network, including structure, tables, graphics etc
	 * @param withTables whether to load node tables from JSON
	 * 
	 * @return Network object
	 * 
	 * @see Node#setTable(JSONObject)
	 * 
	 * @throws NetworkException if failed to create the Network
	 */
	public Network createNetwork(JSONObject jsonNetwork, boolean withTables) throws NetworkException {
		Network network;
		try {
			// Call protected method that actually creates everything in the network
			network = Network.createNetwork(this, jsonNetwork, withTables);
		}
		catch (JSONException ex){
			throw new NetworkException("Failed to create Network", ex);
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
	 * @throws NetworkException if a Network with this ID already exists
	 */
	public Network createNetwork(String id) throws NetworkException {
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
	 * @throws NetworkException if a Network with this ID already exists
	 */
	public Network createNetwork(String id, String name) throws NetworkException {
		synchronized (IdContainer.class){
			if (networks.containsKey(new Id(id))){
				throw new NetworkException("Network with id `" + id + "` already exists");
			}
			networks.put(new Id(id), null);
		}
		
		Network network;
		
		try {
			// Call protected factory method to create a network instance
			network = Network.createNetwork(this, id, name);
			networks.put(new Id(id), network);
		}
		catch (AgenaRiskRuntimeException ex){
			networks.remove(new Id(id));
			throw new NetworkException("Error in AgenaRisk Core `" + id + "`", ex);
		}
		
		return network;
	}
	
	/**
	 * Removes provided Network from this Model, breaks any existing links to and from this Network.
	 * 
	 * @param net Network to remove
	 */
	public void removeNetwork(Network net){
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	/**
	 * Removes provided Network from this Model, breaks any existing links to and from this Network.
	 * 
	 * @param netId ID of the Network to remove
	 */
	public void removeNetwork(String netId){
		removeNetwork(getNetwork(netId));
	}
	
	/**
	 * Creates a JSON representing this Network, ready for file storage.
	 * 
	 * @return JSONObject representing this Network
	 * 
	 * @throws AgenaRiskRuntimeException if failed to convert model to JSON
	 */
	@Override
	public JSONObject toJson() {
		try {
			return JSONAdapter.toJSONObject(logicModel);
		}
		catch (AdapterException | JSONException ex){
			throw new AgenaRiskRuntimeException("Failed to convert model to JSON", ex);
		}
	}

	/**
	 * @throws ModelException when invalid type requested
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public Map<Id,? extends Identifiable> getIdMap(Class<? extends Identifiable> idClassType) throws ModelException {
		if (Network.class.equals(idClassType)){
			return networks;
		}
		
		if (DataSet.class.equals(idClassType)){
			return dataSets;
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
		return networks.get(new Id(id));
	}

	/**
	 * Returns a copy of ID-Network map. Once generated, membership of this map is not maintained.
	 * 
	 * @return copy of ID-Network map
	 */
	public Map<String, Network> getNetworks() {
		return networks.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getValue(), e -> e.getValue(), (i, j) -> i, LinkedHashMap::new));
	}
	
	/**
	 * Returns Networks of this Model as a list
	 * 
	 * @return list of Networks
	 */
	public List<Network> getNetworkList(){
		return new ArrayList<>(networks.values());
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
	 * Triggers propagation in this Model for all Networks and DataSets.
	 * 
	 * @throws CalculationException if calculation failed
	 * @throws InconsistentEvidenceException specifically in case inconsistent evidence was detected
	 */
	public void calculate() throws CalculationException {
		calculate(null, null);
	}
	
	/**
	 * Triggers propagation in this model for provided Networks and DataSets.<br>
	 * If either is null, all Networks or DataSets will be used instead.
	 * 
	 * @param networks Networks to calculate, can be null for all Networks
	 * @param dataSets DataSets to calculate, can be null for all DataSets
	 * @param flags Calculation flags
	 * 
	 * @throws CalculationException if calculation failed
	 * @throws InconsistentEvidenceException specifically in case inconsistent evidence was detected
	 */
	public void calculate(Collection<Network> networks, Collection<DataSet> dataSets, CalculationFlag... flags) throws CalculationException {
		
		if (networks == null){
			networks = getNetworks().values();
		}
		
		if (dataSets == null){
			dataSets = getDataSets().values();
		}
		
		if (networks.isEmpty()){
			throw new CalculationException("No networks in the model, nothing to calculate");
		}
		
		if (!networks.stream().map(net -> net.getNodes().size()).anyMatch(i -> i > 0)){
			throw new CalculationException("No nodes in the model, nothing to calculate");
		}
		
		if (dataSets.isEmpty()){
			Logger.logIfDebug("No Data Sets in model, creating");
			createDataSet("Scenario 1");
		}
		
		networks.stream().forEach(net -> {
			// Force to recalculate
			String s = "Calculation requested";
			net.getLogicNetwork().addModificationLogItem(new NameDescription(s, s));
		});
		
		boolean SimulationSettingWarningMessage = getLogicModel().SimulationSettingWarningMessage;
		boolean checkMonitorsOpen = uk.co.agena.minerva.model.Model.checkMonitorsOpen;
		String suppressMessages = uk.co.agena.minerva.model.Model.suppressMessages;

		getLogicModel().SimulationSettingWarningMessage = false;
		uk.co.agena.minerva.model.Model.checkMonitorsOpen = false;
		uk.co.agena.minerva.model.Model.suppressMessages = "system";
		
		//EnumSet<CalculationFlag> xflags = (flags.length > 0) ? EnumSet.copyOf(Arrays.asList(flags)) : EnumSet.noneOf(CalculationFlag.class);
		List<PropagationFlag> flagsToPass = new ArrayList();
		for (CalculationFlag flag: flags) {
			switch(flag){
				case KEEP_TAILS_ZERO_REGIONS:
					flagsToPass.add(PropagationFlag.KEEP_TAILS_ZERO_REGIONS);
					break;
					
				case WITH_ANCESTORS:
					flagsToPass.add(PropagationFlag.WITH_ANCESTORS);
					break;
				default:
			}
		}
		
		StreamInterceptor.output_capture();
		String outputCaptured = "";
		try {
			getLogicModel().propagateDDAlgorithm(
					dataSets.stream().map(ds -> ds.getLogicScenario()).collect(Collectors.toList()),
					networks.stream().map(net -> net.getLogicNetwork()).collect(Collectors.toList()),
					flagsToPass.toArray(new PropagationFlag[0])
			);
			outputCaptured += StreamInterceptor.output_release();
		}
		catch (Throwable ex){
			outputCaptured += StreamInterceptor.output_release();
			throw new CalculationException("Calculation failed", ex);
		}
		finally {
			getLogicModel().SimulationSettingWarningMessage = SimulationSettingWarningMessage;
			uk.co.agena.minerva.model.Model.checkMonitorsOpen = checkMonitorsOpen;
			uk.co.agena.minerva.model.Model.suppressMessages = suppressMessages;
		}
		
		if (!getLogicModel().isLastPropagationSuccessful()){
			Logger.logIfDebug("Last propagation is not flagged as successful:");
			Logger.logIfDebug(outputCaptured);
			String message = "Calculation failed";
			if (outputCaptured.contains("Inconsistent evidence in risk object")){
				throw new InconsistentEvidenceException("Inconsistent evidence detected (observations resulting in mutually exclusive state combinations)");
			}
			
			if (outputCaptured.contains("node has sum zero probability")){
				message = outputCaptured.replaceFirst("(?s).*(?=(The entire node probability table for))", "");
				message = message.replaceFirst("(?s)(?<=(\\[Normal cannot have zero variance\\]\\.)).*", "");
				message = message.replaceAll("<br/?>", "\n");
				throw new InconsistentEvidenceException(message);
			}
			
			throw new CalculationException(message);
		}
	}
	
	/**
	 * Saves the Model to a file path specified.<br>
	 * Output format is determined by path extension:<br>
	 * • AgenaRisk 7 CMP for "cmp"<br>
	 * • XML for "xml"<br>
	 * • JSON for everything else<br>
	 * 
	 * @param path the file path to save to
	 * 
	 * @throws FileIOException if saving fails
	 */
	public void save(String path) throws FileIOException {
		try {
			if (path.toLowerCase().endsWith(".cmp")){
				getLogicModel().save(path);
			}
			else {
				JSONObject json = JSONAdapter.toJSONObject(logicModel);
				
				String content;
				
				if (path.toLowerCase().endsWith(".xml")){
					content = XMLAdapter.toXMLString(json);
				}
				else {
					content = json.toString();
				}
				
				Files.write(Paths.get(path), content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
		}
		catch (FileHandlingException | AdapterException | IOException | JSONException ex){
			throw new FileIOException("Failed to save the model", ex);
		}
	}
	
	/**
	 * Returns the Model as a minimal JSON.<br>
	 * Keeps only elements essential for a clean calculation.<br>
	 * 
	 * @param flags flags specifying which attributes to keep
	 * 
	 * @return minimal JSON
	 * 
	 * @throws AdapterException if conversion to JSON fails
	 */
	public JSONObject export(ExportFlag... flags) throws AdapterException {
		
		JSONObject json = JSONAdapter.toJSONObject(logicModel);
		
		EnumSet<ExportFlag> xflags = (flags.length > 0) ? EnumSet.copyOf(Arrays.asList(flags)) : EnumSet.noneOf(ExportFlag.class);
		
		try {

			JSONObject jsonModel = json.optJSONObject(Model.Field.model.toString());
			JSONArray jsonDataSets = jsonModel.optJSONArray(DataSet.Field.dataSets.toString());

			if (jsonDataSets != null){
				
				if (jsonDataSets.length() > 0 && xflags.contains(ExportFlag.CLOUD_DATASET)){
					// If there are data sets and cloud flag set, copy the first data set without results to root json
					JSONObject jDataSet = jsonDataSets.optJSONObject(0);
					json.put(DataSet.Field.dataSet.toString(), jDataSet);
					jDataSet = json.getJSONObject(DataSet.Field.dataSet.toString());
					jDataSet.remove(CalculationResult.Field.results.toString());
				}
				
				if (!xflags.contains(ExportFlag.KEEP_RESULTS) && !xflags.contains(ExportFlag.KEEP_OBSERVATIONS)){
					// If there are data sets but we are not keeping results nor observations, remove all data sets
					jsonModel.remove(DataSet.Field.dataSets.toString());
				}
				else {
					jsonDataSets.forEach(o -> {
						if (o instanceof JSONObject){
							JSONObject jDataSet = (JSONObject) o;
							if (!xflags.contains(ExportFlag.KEEP_RESULTS)){
								jDataSet.remove(CalculationResult.Field.results.toString());
							}
							
							if (!xflags.contains(ExportFlag.KEEP_OBSERVATIONS)){
								jDataSet.remove(Observation.Field.observations.toString());
							}
						}
					});
				}
			}
			
			if (!xflags.contains(ExportFlag.KEEP_RISK_TABLE)){
				jsonModel.remove(RiskTable.Field.riskTable.toString());
			}
			
			if (!xflags.contains(ExportFlag.KEEP_META)){
				jsonModel.remove(Audit.Field.audit.toString());
				jsonModel.remove(Meta.Field.meta.toString());
			}

			JSONUtils.traverse(json, (obj -> {
				if (obj instanceof JSONObject){
					JSONObject jo = ((JSONObject) obj);
					if (!xflags.contains(ExportFlag.KEEP_META)){
						if (jo.has(Network.Field.id.toString())){
							// If the object has an ID, we can remove its name and description
							jo.remove(Network.Field.name.toString());
							jo.remove(Network.Field.description.toString());
						}
						jo.remove(Meta.Field.meta.toString());
					}

					// Remove graphics from all objects
					if (!xflags.contains(ExportFlag.KEEP_GRAPHICS)){
						jo.remove(Graphics.Field.graphics.toString());
					}

					jo.remove(Network.ModificationLog.modificationLog.toString());

					if (jo.has(NodeConfiguration.Field.configuration.toString()) && jo.optJSONObject(NodeConfiguration.Field.configuration.toString()).has(NodeConfiguration.Table.table.toString())){
						// Object is node with configuration and table
						JSONObject jsonTable = jo.optJSONObject(NodeConfiguration.Field.configuration.toString()).optJSONObject(NodeConfiguration.Table.table.toString());
						String tableType = jsonTable.optString(NodeConfiguration.Table.type.toString());
						boolean inputNode = jo.optJSONObject(NodeConfiguration.Field.configuration.toString()).optBoolean(NodeConfiguration.Field.input.toString(), false);
						
						if (inputNode || !Objects.equals(tableType, NodeConfiguration.TableType.Manual.toString()) && !xflags.contains(ExportFlag.KEEP_TABLES)){
							// Input node or (not manual table and no keep-tables flag)
							jsonTable.remove(NodeConfiguration.Table.nptCompiled.toString());
							jsonTable.remove(NodeConfiguration.Table.probabilities.toString());
						}
					}
				}
			}));
		}
		catch (NullPointerException | JSONException ex){
			Logger.printThrowableIfDebug(ex);
			return JSONAdapter.toJSONObject(logicModel);
		}
		
		return json;
	}
	
	/**
	 * Saves the Model as a light-weight JSON.<br>
	 * Keeps only elements essential for a clean calculation.<br>
	 * Drops: Graphics, RiskTable, DataSets, compiled non-manual NPTs, names, notes and descriptions.<br>
	 * 
	 * @param path the file path to save to
	 * @param keepMeta Keep meta, names, notes and descriptions of Model, Networks and Nodes
	 * 
	 * @throws FileIOException if saving fails
	 */
	public void saveEssentials(String path, boolean keepMeta) throws FileIOException {
		try {
			
			JSONObject json = export(Model.ExportFlag.CLOUD_DATASET);
			Files.write(Paths.get(path), json.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			
		}
		catch (AdapterException | NullPointerException | IOException ex){
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
	 * @throws DataSetException if a DataSet with this ID already exists
	 */
	public DataSet createDataSet(String id) throws DataSetException {
		synchronized (IdContainer.class){
			if (dataSets.containsKey(new Id(id))){
				throw new DataSetException("DataSet with id `" + id + "` already exists");
			}
			dataSets.put(new Id(id), null);
		}
		
		DataSet dataset = DataSet.createDataSet(this, id);
		dataSets.put(new Id(id), dataset);
		
		return dataset;
	}
	
	/**
	 * Loads Risk Table from JSON into the current Model
	 * 
	 * @param jsonRiskTable the Risk Table data
	 */
	protected void loadRiskTable(JSONArray jsonRiskTable){
		// Clear questionnaires created by default
		getLogicModel().getQuestionnaireList().getQuestionnaires().clear();
		getLogicModel().getMetaData().getRootMetaDataItem().getConnQuestionnaireList().getQuestionnaires().clear();
		for(int i = 0; i < jsonRiskTable.length(); i++){
			this.loadQuestionnaire(jsonRiskTable.optJSONObject(i));
		}
		
		// For each ExtendedBN without a questionnaire, create one
		for (ExtendedBN ebn: (List<ExtendedBN>)getLogicModel().getExtendedBNList().getExtendedBNs()){
			if (getLogicModel().getQuestionnaireList().getQuestionnairesConnectedToExtendedBN(ebn.getId()).isEmpty()){
				Questionnaire q = uk.co.agena.minerva.model.Model.createQuestionnaireFromExtendedBN(ebn);
				q.setSyncToConnectedExBNName(true);
				getLogicModel().getMetaData().getRootMetaDataItem().addQuestionnaire(q,getLogicModel());
			}
			
		}
	}
	
	/**
	 * Loads a Questionnaire from JSON
	 * 
	 * @param jsonQstnr the Questionnaire data
	 */
	protected void loadQuestionnaire(JSONObject jsonQstnr){
		String name = jsonQstnr.optString(RiskTable.Questionnaire.name.toString());
		String description = jsonQstnr.optString(RiskTable.Questionnaire.description.toString());
		Questionnaire qstnr = new Questionnaire(new NameDescription(name, description));
		
		JSONArray jsonQstns = jsonQstnr.optJSONArray(RiskTable.Question.questions.toString());
		if (jsonQstns != null){
			for(int i = 0; i < jsonQstns.length(); i++){
				JSONObject jsonQstn = jsonQstns.optJSONObject(i);
				
				String nameQ = jsonQstn.optString(RiskTable.Question.name.toString());
				String descriptionQ = jsonQstn.optString(RiskTable.Question.description.toString());
				String netId = jsonQstn.optString(RiskTable.Question.network.toString());
				String nodeId = jsonQstn.optString(RiskTable.Question.node.toString());
				
				Network network = getNetwork(netId);
				Node node = network.getNode(nodeId);
				
				Question qstn = new Question(network.getLogicNetwork().getId(), node.getLogicNode().getId(), new NameDescription(nameQ, descriptionQ));
				
				boolean visible = jsonQstn.optBoolean(RiskTable.Question.visible.toString());
				boolean syncToConnectedNodeName = jsonQstn.optBoolean(RiskTable.Question.syncName.toString());
				
				qstn.setVisible(visible);
				qstn.setSyncToConnectedNodeName(syncToConnectedNodeName);
				
				if (node.isSimulated()){
					qstn.setSyncAnswersToNodeStates(true);
				}
				
				String questionMode = jsonQstn.optString(RiskTable.Question.mode.toString());
				String questionType = jsonQstn.optString(RiskTable.Question.type.toString());
				
				if (questionType.equalsIgnoreCase(RiskTable.QuestionType.constant.toString())){
					qstn.setRecommendedAnsweringMode(Question.ANSWER_AS_EXPRESSION_VARIABLE);
					String expressionVariableName = jsonQstn.optString(RiskTable.Question.constantName.toString());
					qstn.setExpressionVariableName(expressionVariableName);
				}
				else {
					if (questionMode.equalsIgnoreCase(RiskTable.QuestionMode.numerical.toString())){
						qstn.setRecommendedAnsweringMode(Question.ANSWER_NUMERICALLY);
					}
					else if (questionMode.equalsIgnoreCase(RiskTable.QuestionMode.unanswerable.toString())){
						qstn.setRecommendedAnsweringMode(Question.ANSWER_BY_UNANSWERABLE);
					}
					else {
						// Fall back to selection option
						qstn.setRecommendedAnsweringMode(Question.ANSWER_BY_SELECTION);
					}
				}
				
				JSONArray jsonAnsws = jsonQstn.optJSONArray(RiskTable.Answer.answers.toString());
				if (jsonAnsws == null || jsonAnsws.length() == 0){
					qstn.setSyncAnswersToNodeStates(true);
				}
				
				boolean syncAnswers = qstn.isSyncAnswersToNodeStates();
				
				if (!qstn.isSyncAnswersToNodeStates()){
					// Read answers
					List<Answer> answs = new ArrayList<>();
					for(int a = 0; a < jsonAnsws.length(); a++){
						JSONObject jsonAnsw = jsonAnsws.optJSONObject(a);
						
						String nameA = jsonAnsw.optString(RiskTable.Answer.name.toString());
						String correspondingStateLabel = jsonAnsw.optString(RiskTable.Answer.state.toString());
						
						ExtendedState correspondingState = null;
								
						for(ExtendedState es: (List<ExtendedState>) node.getLogicNode().getExtendedStates()){
							String computedLabel = State.computeLabel(node.getLogicNode(), es);
							if (computedLabel.equalsIgnoreCase(correspondingStateLabel)){
								correspondingState = es;
								break;
							}
						}
						
						if (correspondingState == null){
							// Did not find the matching state, reset answers
							syncAnswers = true;
							break;
						}
						
						Answer answ = new Answer(correspondingState.getId(), new NameDescription(nameA, ""));
						answs.add(answ);
					}
					qstn.setAnswers(answs);
				}
				
				if (syncAnswers){
					// Sync answers to states
					Question templateQuestion = uk.co.agena.minerva.model.Model.generateQuestionFromNode(network.getLogicNetwork(), node.getLogicNode());
					qstn.setAnswers(templateQuestion.getAnswers());
				}
				
				qstnr.addQuestion(qstn);
			}
		}
		
		if (!qstnr.getQuestions().isEmpty()){
			getLogicModel().getQuestionnaireList().addQuestionnaire(qstnr);
			getLogicModel().getMetaData().getRootMetaDataItem().getConnQuestionnaireList().getQuestionnaires().add(qstnr);
		}
	}
	
	/**
	 * Creates a DataSet for this Model from JSON data.
	 * <br>
	 * If a DataSet with the provided ID already exists, it will append a number to that ID to make it unique.
	 * <br>
	 * If an observation in DataSet fails to be set, it is ignored and results for that DataSet are not loaded.
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
		catch (DataSetException | ModelException ex){
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
		return dataSets.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getValue(), e -> e.getValue(), (i, j) -> i, LinkedHashMap::new));
	}
	
	/**
	 * Returns DataSets of this Model as a list
	 * 
	 * @return list of DataSets
	 */
	public List<DataSet> getDataSetList() {
		return new ArrayList<>(dataSets.values());
	}
	
	/**
	 * Creates a Link between two nodes in same or different Networks.
	 * 
	 * @param source Node to link from
	 * @param target Node to link to
	 * @param type type of CrossNetworkLink; Type can not be CrossNetworkLink.Type.State, use createCrossNetworkLink(Node, Node, String) instead
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if Link already exists, or a cross network link is being created with invalid arguments
	 */
	public Link createLink(Node source, Node target, CrossNetworkLink.Type type) throws LinkException {
		return Node.linkNodes(source, source, type);
	}
	
	/**
	 * Creates a CrossNetworkLink of given Type.
	 *
	 * @param sourceNetworkId ID of source Network of the link
	 * @param sourceNodeId ID of source Node of the link
	 * @param targetNetworkId ID of target Network of the link
	 * @param targetNodeId ID of target Node of the link
	 * @param type type of the message for the link to pass; Type can not be CrossNetworkLink.Type.State, use createCrossNetworkLink(Node, Node, String) instead
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if both Nodes are in the same Network; invalid Type given, or source Node already has incoming links or if Network with this ID does not exist
	 */
	public Link createLink(String sourceNetworkId, String sourceNodeId, String targetNetworkId, String targetNodeId, CrossNetworkLink.Type type) throws LinkException {
		Node source = getNetwork(sourceNetworkId).getNode(sourceNodeId);
		Node target = getNetwork(targetNetworkId).getNode(targetNodeId);
		
		return createLink(source, target, type);
	}
	
	/**
	 * Creates a CrossNetworkLink of type CrossNetworkLink.Type.State that passes the given state from source Node to target Node.
	 * 
	 * @param source source Node of the link
	 * @param target target Node of the link
	 * @param state the state from source Node for the link to pass to target Node
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if both Nodes are in the same Network; invalid state given, or source Node already has incoming links
	 */
	public Link createLink(Node source, Node target, State state) throws LinkException {
		return Node.linkNodes(source, target, CrossNetworkLink.Type.State, state.getLabel());
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
	 * @return created Link
	 * 
	 * @throws ModelException if both Nodes are in the same Network; invalid state given, or source Node already has incoming links
	 * @throws NullPointerException if Network with this ID does not exist
	 */
	public Link createLink(String sourceNetworkId, String sourceNodeId, String targetNetworkId, String targetNodeId, String stateLabel) throws ModelException {
		Node source = getNetwork(sourceNetworkId).getNode(sourceNodeId);
		Node target = getNetwork(targetNetworkId).getNode(targetNodeId);
		
		return Node.linkNodes(source, target, CrossNetworkLink.Type.State, stateLabel);
	}

	/**
	 * Returns Risk Map texts stored as JSON
	 * 
	 * @return Risk Map texts stored as JSON
	 */
	public JSONArray getJsonTexts() {
		return jsonTexts;
	}

	/**
	 * Returns Risk Map pictures stored as JSON
	 * 
	 * @return Risk Map pictures stored as JSON
	 */
	public JSONArray getJsonPictures() {
		return jsonPictures;
	}

	/**
	 * Returns model graphics stored as JSON
	 * 
	 * @return model graphics stored as JSON
	 */
	public JSONObject getJsonGraphics() {
		return jsonGraphics;
	}

	/**
	 * Returns audit stored as JSON
	 * 
	 * @return audit stored as JSON
	 */
	public JSONObject getJsonAudit() {
		return jsonAudit;
	}

	/**
	 * Links this Model to an underlying Minerva Model object. Should only be used while wrapping a new Model around the Minerva Model.
	 * 
	 * @param logicModel the logical model
	 */
	protected void setLogicModel(uk.co.agena.minerva.model.Model logicModel) {
		this.logicModel = logicModel;
	}
	
	/**
	 * Looks up and returns a DataSet by its ID.
	 * 
	 * @param id the ID of the DataSet to return
	 * 
	 * @return the DataSet identified by ID or null, if no such DataSet exists in this Model
	 */
	public DataSet getDataSet(String id){
		return dataSets.get(new Id(id));
	}
	
	/**
	 * Performs binary factorization on the model if there are any simulation nodes with more than 3 parents.<br>
	 * This involves the model being recreated from scratch and all previously held references to pre-existing objects in the Model will become invalid and should be released.
	 * 
	 * @return false if no factorization was required, true if successfully factorized
	 * 
	 * @throws com.agenarisk.api.exception.ModelException if failed to factorize
	 */
	public boolean factorize() throws ModelException{
		
		BinaryBNConverter converter = new BinaryBNConverter(getLogicModel(), false);
		
		// List of networks to process (those that need factorisation and its ancestors); array of flags to indicate which networks are being factorised
		List<Network> networksList = getNetworkList();
		List<ExtendedBN> bnList = new ArrayList<>();
		Boolean[] factorizeFlags = new Boolean[networks.size()];
		boolean factorizeAny = false;
		
		for(int i = 0; i < networks.size(); i++){
			Network net = networksList.get(i);
			try {
				net.getLogicNetwork().checkExpressions();
			}
			catch (ExtendedBNException | JepException ex){
				throw new ModelException("Can't factorize the model due to invalid expression", ex);
			}
				
			bnList.add(net.getLogicNetwork());
			boolean factorize = net.getNodes().values().stream().anyMatch(node -> {
				if (!node.isSimulated() || node.getLinksIn().size() <= 2){
					// Only factorize simulated nodes
					// If 2 parents or less no need to factorize at all
					return false;
				}
				
				if (!node.getTableType().equals(NodeConfiguration.TableType.Expression)){
					// Only factorize nodes with a single expression - not a manual NPT and not a partitioned expression
					return false;
				}
				
				if (node.getLogicNode() instanceof ContinuousEN){
					ContinuousEN cien = (ContinuousEN)node.getLogicNode();
					if (cien.checkExpressionToDetectComplexFunction()){
						// The node contains an expression that can't be factorized
						return false;
					}
				}
				
				// At this point there are more than 2 parents
				// The NPT type is expression
				// All parents are intervals (sim or non-sim) because otherwise the node would have to be partitioned expression
				
				Set<Node> parents = node.getParents();
				
				// Only factorize if the number of simulated parents is more than 2
				return parents.stream().filter(Node::isSimulated).count() > 2;
				
			});			
			factorizeFlags[i] = factorize;
			if (factorize){
				factorizeAny = true;
			}
		}
		
		if (!factorizeAny){
			return false;
		}
		
		JSONObject settingsOriginal = Settings.toJson(logicModel);
		
		try {
			// Call to perform actual factorization (a factorized model is saved to disk)
			converter.convertBNList(bnList, logicModel, factorizeFlags);
			
			// Load the factorized version from disk and convert to JSON
			uk.co.agena.minerva.model.Model binaryModel = uk.co.agena.minerva.model.Model.load(logicModel.getFactorizedBFModelPath());
			JSONObject jsonBinary = JSONAdapter.toJSONObject(binaryModel);
			
			// Reset the current model and rebuild from the JSON structure
			// We do this to preserve the reference to this object and keep using this instance
			reset();
			absorb(jsonBinary);
			
			Settings.loadSettings(this, settingsOriginal);
		}
		catch (Exception ex){
			throw new ModelException("Failed to factorize", ex);
		}
		
		return true;
	}
	
	/**
	 * Removes the provided DataSet from the model
	 * 
	 * @param dataSet DataSet to remove
	 * 
	 * @return true if the DataSet was removed
	 */
	public boolean removeDataSet(DataSet dataSet){
		try {
			this.logicModel.removeScenario(dataSet.getLogicScenario());
			this.dataSets.remove(new Id(dataSet.getId()));
		}
		catch (ScenarioNotFoundException ex){
			return false;
		}
		return true;
	}
	
	/**
	 * Make all states of dynamically discretized nodes static as they currently are in the provided DataSet.<br>
	 * No action will be performed if no nodes are simulated.<br>
	 * Any VariableObservations in the DataSet will replace current Node variable defaults and will be replaced from the DataSet observations.
	 * 
	 * @param dataSet DataSet to use for creating static states from results
	 * 
	 * @throws AgenaRiskRuntimeException if failed to regenerate NPTs after conversion
	 * @throws NodeException if failed for other reasons
	 */
	public void convertToStatic(DataSet dataSet) throws NodeException, AgenaRiskRuntimeException {
		getNetworks().values().forEach(network -> {
			network.getNodes().values().forEach(node -> {
				if (node.isSimulated()){
					try {
						node.convertToStatic(dataSet);
					}
					catch (Exception ex){
						throw new NodeException("Failed to convert results to static states for node " + node.toStringExtra() + " from DataSet `" + dataSet.getId() + "`", ex);
					}
				}
			});
		});
		
		try {
			getLogicModel().getExtendedBNList().regenerateNPTforEveryExtendedNode(false);
			getLogicModel().fireModelChangedEvent(getLogicModel(), uk.co.agena.minerva.model.ModelEvent.ALL_NPTS_CHANGED, new ArrayList());
		}
		catch (Exception ex){
			throw new AgenaRiskRuntimeException("Failed to regenerate NPTs", ex);
		}
	}
	
	/**
	 * Reset the model by removing all Networks, Links, DataSets etc
	 */
	public void reset(){
		networks.clear();
		dataSets.clear();
		jsonTexts = null;
		jsonPictures = null;
		jsonGraphics = null;
		jsonMeta = null;
		jsonAudit = null;
		logicModel.destroy();
		initLogicModel();
	}
	
	/**
	 * Replaces the logic model with a new one and removes default network and dataset
	 */
	private void initLogicModel(){
		String outputMode = "system";
		if (Environment.isGuiMode()){
			outputMode = "all";
		}
		
		try {
			logicModel = uk.co.agena.minerva.model.Model.createEmptyModel(outputMode);
			// Remove the default network
			logicModel.removeExtendedBNs(logicModel.getExtendedBNAtIndex(0), true);
			// Remove the default data set
			logicModel.removeScenario(logicModel.getScenarioAtIndex(0));
		}
		catch (uk.co.agena.minerva.model.ModelException | ScenarioNotFoundException ex){
			throw new AgenaRiskRuntimeException("Failed to initialise the model", ex);
		}
	}
	
	/**
	 * Returns Model Settings object.
	 * 
	 * @return Model Settings object
	 */
	public Settings getSettings(){
		return new Settings(this);
	}
	
	/**
	 * Checks whether the model had been successfully calculated and requires no calculation at this time.<br>
	 * Model is considered calculated if all Networks had been calculated.
	 * 
	 * @return true if the model is in a calculated state and requires no further calculation
	 */
	public boolean isCalculated(){
		if (!getLogicModel().isLastPropagationSuccessful()){
			return false;
		}
		
		// Check modification log
		for(Network net: networks.values()){
			try {
				boolean netModified = net.getLogicNetwork().getModificationLog().getModificationItems().isEmpty();
				if (netModified){
					return false;
				}
			}
			catch (NullPointerException ex){
				// Not modified, ignore
			}
		}
		
		return true;
	}
	
	/**
	 * Generates a new DataSet ID that is guaranteed to be available at the return time.
	 * 
	 * @param prefix String to prefix the generated ID
	 * 
	 * @return new available DataSet ID
	 */
	public String getAvailableDataSetId(String prefix){
		synchronized (IdContainer.class){
			if (prefix == null || prefix.trim().isEmpty()){
				prefix = "Data Set";
			}

			String id = prefix;
			int counter = 1;

			while(getDataSet(id) != null){
				id = prefix + " " + counter;
				counter++;
			}

			return id;
		}
	}
	
	
}
