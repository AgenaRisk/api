package com.agenarisk.api.model;

import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.DataSetException;
import com.agenarisk.api.exception.FileIOException;
import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.io.FileAdapter;
import com.agenarisk.api.io.JSONAdapter;
import com.agenarisk.api.io.stub.Audit;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.Picture;
import com.agenarisk.api.io.stub.RiskTable;
import com.agenarisk.api.io.stub.Text;
import com.agenarisk.api.model.field.Id;
import com.agenarisk.api.model.interfaces.IDContainer;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.JSONUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.model.MessagePassingLinkException;
import uk.co.agena.minerva.model.PropagationException;
import uk.co.agena.minerva.model.PropagationTerminatedException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.questionnaire.Answer;
import uk.co.agena.minerva.model.questionnaire.Question;
import uk.co.agena.minerva.model.questionnaire.Questionnaire;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.StreamInterceptor;
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
	public final Map<Id, Network> networks = Collections.synchronizedMap(new LinkedHashMap<>());
	
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
		
		Model model = null;
		
		if (path.toLowerCase().endsWith(".cmp")){
			try {
				model = Model.createModel(uk.co.agena.minerva.model.Model.load(path, uk.co.agena.minerva.model.Model.suppressMessages));
			}
			catch (Exception ex){
				throw new ModelException("Filed to convert CMP model data", ex);
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
					
					JSONObject jsonConfiguration = jsonNode.getJSONObject(NodeConfiguration.Field.configuration.toString());
					JSONObject jsonTable = jsonConfiguration.optJSONObject(NodeConfiguration.Table.table.toString());
					try {
						if (!node.getLogicNode().isConnectableInputNode()){
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
	 * Note: loading node tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
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
	 * @throws ModelException
	 */
	public Network createNetwork(JSONObject jsonNetwork, boolean withTables) throws ModelException {
		Network network;
		try {
			// Call protected method that actually creates everything in the network
			network = Network.createNetwork(this, jsonNetwork, withTables);
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
			if (networks.containsKey(new Id(id))){
				throw new ModelException("Network with id `" + id + "` already exists");
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
		
		StreamInterceptor.output_capture();
		String outputCaptured = "";
		try {
			getLogicModel().calculate();
		}
		catch (ExtendedBNException | MessagePassingLinkException | PropagationException | PropagationTerminatedException ex){
			outputCaptured = StreamInterceptor.output_release();
			throw new CalculationException("Calculation failed", ex);
		}
		outputCaptured += StreamInterceptor.output_release();
		
		if (!getLogicModel().isLastPropagationSuccessful()){
			String message = "Calculation failed";
			if (outputCaptured.contains("Inconsistent evidence in risk object")){
				message = "Inconsistent evidence detected (observations resulting in mutually exclusive state combinations)";
			}
			throw new CalculationException(message);
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
			if (dataSets.containsKey(new Id(id))){
				throw new ModelException("DataSet with id `" + id + "` already exists");
			}
			dataSets.put(new Id(id), null);
		}
		
		DataSet dataset;
		
		try {
			dataset = DataSet.createDataSet(this, id);
			dataSets.put(new Id(id), dataset);
		}
		catch (AgenaRiskRuntimeException ex){
			dataSets.remove(new Id(id));
			throw new ModelException("Failed to add DataSet `" + id + "`", ex);
		}
		
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
	
}
