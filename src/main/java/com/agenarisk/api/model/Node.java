package com.agenarisk.api.model;

import com.agenarisk.api.model.interfaces.Named;
import com.agenarisk.api.model.interfaces.Networked;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.JSONUtils;
import com.agenarisk.api.util.Advisory;
import com.singularsys.jep.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.model.corebn.CoreBNNodeNotFoundException;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateException;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNumberingException;
import uk.co.agena.minerva.model.extendedbn.NumericalEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.io.JSONAdapter;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.NodeGraphics;
import com.agenarisk.api.model.field.Id;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import uk.co.agena.minerva.model.MarginalDataItemList;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.helpers.MathsHelper;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.MinervaVariableException;
import uk.co.agena.minerva.util.model.NameDescription;
import uk.co.agena.minerva.util.model.Range;
import uk.co.agena.minerva.util.model.VariableList;
import uk.co.agena.minerva.util.nptgenerator.ExpressionParser;

/**
 * Node class represents a node in the Network.
 * 
 * @author Eugene Dementiev
 */
public class Node implements Networked<Node>, Comparable<Node>, Identifiable<NodeException>, Storable, Named {
	
	/**
	 * These are Node types that correspond to AgenaRisk Desktop node types
	 */
	public static enum Type {
		Boolean,
		Labelled,
		Ranked,
		DiscreteReal,
		ContinuousInterval,
		IntegerInterval
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		nodes,
		node,
		id,
		name,
		description
	}
	
	/**
	 * The Network containing this Node
	 */
	private final Network network;
	
	/**
	 * Incoming Links from other Nodes
	 */
	private final Set<Link> linksIn = Collections.synchronizedSet(new LinkedHashSet());
	
	/**
	 * Outgoing Links to other Nodes
	 */
	private final Set<Link> linksOut = Collections.synchronizedSet(new LinkedHashSet<>());
	
	/**
	 * Corresponding ExtendedNode
	 */
	private ExtendedNode logicNode;
	
	/**
	 * This stores meta tag for the Node, and should be set on model load
	 */
	private JSONObject jsonMeta;
	
	/**
	 * This stores graphics for the Node, and should be set on model load
	 */
	private JSONObject jsonGraphics = new JSONObject();
	
	/**
	 * Cache of Variables
	 */
	private final Map<String, Variable> variablesCache = Collections.synchronizedMap(new LinkedHashMap<>());
	
	/**
	 * Constructor for the Node class. Creates a Node object without affecting the logic in any way.
	 * 
	 * @param network the Network that will contain this Node
	 * @param logicNode the Node's corresponding logic Node
	 */
	private Node(Network network, String id, String name, Type type) {
		String nodeClassName = NodeConfiguration.resolveNodeClassName(type);
		
		ExtendedNode en;
		try {
			en = network.getLogicNetwork().createNewExtendedNode(nodeClassName, new NameDescription(name, ""));
			network.getLogicNetwork().updateConnNodeId(en, id);
		}
		catch (CoreBNNodeNotFoundException | ExtendedBNException ex){
			throw new AgenaRiskRuntimeException("Failed to create a node for ID `"+id+"`", ex);
		}

		this.network = network;
		this.logicNode = en;
		
		createDefaultStates();
	}
	
	/**
	 * Returns a copy of the list of incoming Links. The membership is only guaranteed to be valid at the time of request and is not maintained.
	 * 
	 * @return list of incoming Links
	 */
	@Override
	public synchronized List<Link> getLinksIn() {
		return Collections.unmodifiableList(new ArrayList<>(linksIn));
	}

	/**
	 * Returns a copy of the list of outgoing Links. The membership is only guaranteed to be valid at the time of request and is not maintained.
	 * 
	 * @return list of outgoing Links
	 */
	@Override
	public synchronized List<Link> getLinksOut() {
		return Collections.unmodifiableList(new ArrayList<>(linksOut));
	}
	
	/**
	 * Builds and returns a set of Node's parents, which is valid at the time of request. This set will not reflect any membership changes made afterwards.
	 * 
	 * @return a set of Node's parents
	 */
	@Override
	public synchronized Set<Node> getParents() {
		return getLinksIn().stream().map(link -> link.getFromNode()).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * Builds and returns a set of Node's children, which is valid at the time of request. This set will not reflect any membership changes made afterwards.
	 * 
	 * @return a set of Node's children
	 */
	@Override
	public synchronized Set<Node> getChildren() {
		return getLinksOut().stream().map(link -> link.getToNode()).collect(Collectors.toCollection(LinkedHashSet::new));
	}
	
	/**
	 * Removes the Link object from the linksOut list.
	 * 
	 * @param link the Link to remove
	 * 
	 * @return true if Link was added and false if the Link does not concern this Node
	 */
	protected final boolean addLink(Link link){
		if (link.getFromNode().equals(this)){
			linksOut.add(link);
			return true;
		}
		
		if (link.getToNode().equals(this)){
			linksIn.add(link);
			return true;
		}
		
		// The Link does not concern this Node
		return false;
	}
	
	/**
	 * Removes the Link object from the linksOut list.
	 * 
	 * @param link the Link to remove
	 * 
	 * @return true if Link was removed and false if the Link does not concern this Node
	 */
	protected final boolean removeLink(Link link){
		if (link.getFromNode().equals(this)){
			linksOut.remove(link);
			return true;
		}
		
		if (link.getToNode().equals(this)){
			linksIn.remove(link);
			return true;
		}
		
		// The Link does not concern this Node
		return false;
	}
	
	/**
	 * Creates a simple Link from this Node to given Node in the same Network.
	 * <br>
	 * If child table is in Manual mode, this action will reset it to a uniform table.
	 * 
	 * @param child the child Node
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if Link already exists, or a cross network link is being created (use Node.linkNodes() for that instead)
	 */
	public synchronized Link linkTo(Node child) throws LinkException {
		return Node.linkNodes(this, child);
	}
	
	/**
	 * Creates a simple Link from the given Node to this Node in the same Network.
	 * <br>
	 * If this Node's table is in Manual mode, this action will reset it to a uniform table.
	 * 
	 * @param parent the parent Node
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if Link already exists, or a cross network link is being created (use Node.linkNodes() for that instead)
	 */
	public synchronized Link linkFrom(Node parent) throws LinkException {
		return Node.linkNodes(parent, this);
	}
	
	/**
	 * Checks that there is a Link between two nodes and destroys it. No effect if there is no link between these two nodes.
	 * 
	 * @param node1 Node 1
	 * @param node2 Node 2
	 * 
	 * @return true if the link was destroyed, false if there was no link
	 */
	public static boolean unlinkNodes(Node node1, Node node2){
		List<Link> links = node1.getLinksIn();
		links.addAll(node1.getLinksOut());
		
		Link link = null;
		for(Link l: links){
			if (l.getFromNode().equals(node1) && l.getToNode().equals(node2) || l.getFromNode().equals(node2) && l.getToNode().equals(node1)){
				link = l;
			}
		}
		
		if (link == null){
			return false;
		}
		
		node1.removeLink(link);
		node2.removeLink(link);
		link.destroyLogicLink();
		
		return true;
	}
	
	/**
	 * Creates links from the given JSON.
	 * <br>
	 * Does nothing if JSON is null or empty.
	 * 
	 * @param model the model to create Links in
	 * @param jsonLinks configuration of the Links
	 * 
	 * @throws JSONException if JSON structure is invalid or inconsistent
	 * @throws LinkException if a Link fails to be created
	 * @throws NodeException if failed to create a link between nodes
	 */
	public static void linkNodes(Model model, JSONArray jsonLinks) throws JSONException, LinkException, NodeException {
		if (jsonLinks == null){
			return;
		}
		
		for (int i = 0; i < jsonLinks.length(); i++) {
			JSONObject jsonLink = jsonLinks.getJSONObject(i);
			String net1Id = jsonLink.getString(CrossNetworkLink.Field.sourceNetwork.toString());
			String node1Id = jsonLink.getString(CrossNetworkLink.Field.sourceNode.toString());
			
			String net2Id = jsonLink.getString(CrossNetworkLink.Field.targetNetwork.toString());
			String node2Id = jsonLink.getString(CrossNetworkLink.Field.targetNode.toString());
			
			Node sourceNode;
			Node targetNode;
			try {
				sourceNode = model.getNetwork(net1Id).getNode(node1Id);
				sourceNode.getId();
				targetNode = model.getNetwork(net2Id).getNode(node2Id);
				targetNode.getId();
			}
			catch (NullPointerException ex){
				throw new LinkException("Network or node not found", ex);
			}
			
			CrossNetworkLink.Type linkType = CrossNetworkLink.Type.Marginals;
			
			try {
				String linkTypeString = jsonLink.getString(CrossNetworkLink.Field.type.toString());
				try {
					linkType = CrossNetworkLink.Type.valueOf(linkTypeString);
				}
				catch (IllegalArgumentException ex){
					throw new LinkException("Invalid link type `" + linkTypeString + "`", ex);
				}
			}
			catch (JSONException ex){
				Logger.logIfDebug("No cross network link type provided, defaulting to type = Marginals");
			}
			
			String stateId = jsonLink.optString(CrossNetworkLink.Field.passState.toString(), null);
			
			try {
				Node.linkNodes(sourceNode, targetNode, linkType, stateId);
			}
			catch (LinkException ex){
				throw new NodeException("Failed to create a link between nodes " + sourceNode + " and " + targetNode, ex);
			}
			
		}
	}
	
	/**
	 * Creates a simple Link between two nodes in the same Network.
	 * 
	 * @param fromNode Node to link from
	 * @param toNode Node to link to
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if Link already exists, or a cross network link is being created
	 */
	public static Link linkNodes(Node fromNode, Node toNode) throws LinkException {
		return linkNodes(fromNode, toNode, null, null);
	}
	
	/**
	 * Creates a Link between two nodes in same or different Networks.
	 * 
	 * @param fromNode Node to link from
	 * @param toNode Node to link to
	 * @param type type of CrossNetworkLink if applicable
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if Link already exists, or a cross network link is being created with invalid arguments
	 */
	public static Link linkNodes(Node fromNode, Node toNode, CrossNetworkLink.Type type) throws LinkException {
		return linkNodes(fromNode, toNode, type, null);
	}
	
	/**
	 * Creates a Link between two nodes in same or different Networks.
	 * <br>
	 * The underlying NPT of the child node <b>will be reset</b> to some default value.
	 * 
	 * @param fromNode Node to link from
	 * @param toNode Node to link to
	 * @param type type of CrossNetworkLink if applicable
	 * @param stateToPass if type is State, this specifies the state to pass
	 * 
	 * @return created Link
	 * 
	 * @throws LinkException if Link already exists, or a cross network link is being created with invalid arguments
	 */
	public static Link linkNodes(Node fromNode, Node toNode, CrossNetworkLink.Type type, String stateToPass) throws LinkException {
		// Sync on class to prevent multiple links established by multiple threads
		synchronized(Network.class){
			
			if (fromNode.getChildren().contains(toNode)){
				throw new LinkException("Link already exists");
			}
			
			if (Objects.equals(fromNode, toNode)){
				throw new LinkException("Trying to link node to itself");
			}
			
			Link link;
			
			boolean crossNetworkLink = !Objects.equals(fromNode.getNetwork(), toNode.getNetwork());
			
			if (toNode.isConnectedInput()){
				// Can't link to an input Node that already has a Link coming in
				throw new LinkException("Node " + fromNode.toStringExtra() + " already has an incoming cross network link");
			}
			
			if (crossNetworkLink){
				
				if (!toNode.getLinksIn().isEmpty()){
					// Input node can't have multiple links in
					throw new LinkException("Can't add a cross network link to " + toNode.toStringExtra() + " because it already has parents");
				}
				
				if (toNode.getLogicNode().isConnectableOutputNode() && !toNode.getLinksOut().isEmpty()){
					// Input node can't also be output node
					throw new LinkException("Node " + toNode.toStringExtra() + " already appears to be configured for cross network outgoing link");
				}
				
				if (fromNode.getLogicNode().isConnectableInputNode() && !toNode.getLinksIn().isEmpty()){
					// Output node can't also be input node
					throw new LinkException("Node " + fromNode.toStringExtra() + " already appears to be configured for cross network incoming link");
				}
				
				if (
						Objects.equals(fromNode.getType(), toNode.getType()) && (fromNode.isSimulated() || toNode.isSimulated())
						|| Objects.equals(fromNode.getType(), toNode.getType()) && !fromNode.isSimulated() && !toNode.isSimulated() && fromNode.getStates().size() == toNode.getStates().size()
						|| !Arrays.asList(new Node.Type[]{Node.Type.ContinuousInterval, Node.Type.IntegerInterval, Node.Type.DiscreteReal}).contains(fromNode.getType()) && toNode.isSimulated()
//						){
//				}
//				
//				if (Objects.equals(fromNode.getType(), toNode.getType()) && Objects.equals(fromNode.isSimulated(), toNode.isSimulated()) /* Same type and same simulation */
//						|| !fromNode.isNumericInterval() && !Type.DiscreteReal.equals(fromNode.getType()) && toNode.isSimulated() /* Not numeric going to simulation */
//						|| Objects.equals(fromNode.getType(), toNode.getType()) && fromNode.isSimulated() /* Numeric sim interval going into same non-sim type */
					){
					// OK
				}
				else {
					throw new LinkException("Cross network link not allowed between nodes (" + fromNode.toStringExtra() + " is " + fromNode.getType() + ", " + toNode.toStringExtra() + " is " + toNode.getType() + "), see documentation");
				}
				
				if (!fromNode.isSimulated() && !toNode.isSimulated() && fromNode.getLogicNode().getExtendedStates().size() != toNode.getLogicNode().getExtendedStates().size()){
					throw new LinkException("Cross network link can only be created between nodes with the same number of states");
				}
				
				link = CrossNetworkLink.createCrossNetworkLink(fromNode, toNode, type, stateToPass);
			}
			else {
				if (fromNode.getChildren().contains(toNode)){
					throw new LinkException("A link between " + fromNode.toStringExtra() + " and " + toNode.toStringExtra() + " already exists");
				}
				
				link = Link.createLink(fromNode, toNode);
				
			}
			
			fromNode.addLink(link);
			if (type == null){
				// Inner network link
				if (fromNode.hasDescendant(fromNode)){
					fromNode.removeLink(link);
					throw new LinkException("This link would create a loop in the network");
				}
			}
			else {
				// Cross network link
				if (fromNode.getNetwork().hasDescendant(fromNode.getNetwork())){
					fromNode.removeLink(link);
					throw new LinkException("This link would create a loop between networks");
				}
			}
			
			toNode.addLink(link);
			
			try {
				link.createLogicLink();
			}
			catch (LinkException ex){
				// Logic link creation failed, roll back links and destroy
				fromNode.removeLink(link);
				toNode.removeLink(link);
				
				// Throw the exception up the stack
				throw ex;
			}
			
			return link;
		}
	}
	
	/**
	 * Factory method to be called by a Network object that is trying to add a Node to itself.
	 * 
	 * @param network the network to add a node to
	 * @param id the ID of the node
	 * @param name the name of the node
	 * @param type type of the Node to create
	 * 
	 * @return the created Node
	 */
	protected static Node createNode(Network network, String id, String name, Type type) {
		return new Node(network, id, name, type);
	}
	
		/**
	 * Factory method to create a Node for use by the Network class.
	 * <br>
	 * Creates the underlying logic objects.
	 * <br>
	 * Note: loading tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param network Network that the Node will be added to
	 * @param jsonNode configuration of the Node
	 * 
	 * @return created Node
	 * 
	 * @see #setTable(JSONObject)
	 * 
	 * @throws NodeException if failed to create the node
	 * @throws JSONException if JSON configuration is incomplete or invalid
	 */
	protected static Node createNode(Network network, JSONObject jsonNode) throws NodeException, JSONException {
		return createNode(network, jsonNode, true);
	}
	
	/**
	 * Factory method to create a Node for use by the Network class.
	 * <br>
	 * Creates the underlying logic objects.
	 * <br>
	 * Note: loading tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param network Network that the Node will be added to
	 * @param jsonNode configuration of the Node
	 * @param withTables whether to load node tables from JSON
	 * 
	 * @return created Node
	 * 
	 * @see #setTable(JSONObject)
	 * 
	 * @throws NodeException if failed to create the node
	 * @throws JSONException if JSON configuration is incomplete or invalid
	 */
	protected static Node createNode(Network network, JSONObject jsonNode, boolean withTables) throws NodeException, JSONException {
		String id = jsonNode.getString(Field.id.toString());
		String name = jsonNode.optString(Field.name.toString());
		String description = jsonNode.optString(Field.description.toString());
		
		if (name.isEmpty()){
			name = id;
		}
		
		JSONObject jsonConfiguration = jsonNode.optJSONObject(NodeConfiguration.Field.configuration.toString());
		if (jsonConfiguration == null){
			jsonConfiguration = new JSONObject();
		}
		String typeString = jsonConfiguration.optString(NodeConfiguration.Field.type.toString(), Node.Type.Boolean.toString());
		Type type = Type.valueOf(typeString);
		
		Node node;
		try {
			node = network.createNode(id, name, type);
			node.setDescription(description);
		}
		catch (NetworkException ex){
			throw new NodeException("Failed to add a node to network", ex);
		}
		
		ExtendedNode en = node.getLogicNode();
		
		boolean simulated = jsonConfiguration.optBoolean(NodeConfiguration.Field.simulated.toString(), false);
		
		if (simulated){
			ContinuousEN cien = (ContinuousEN)en;
			NodeConfiguration.setDefaultIntervalStates(node);
			cien.setSimulationNode(true);
			
			if (jsonConfiguration.has(NodeConfiguration.Field.simulationConvergence.toString())){
				cien.setEntropyConvergenceThreshold(jsonConfiguration.getDouble(NodeConfiguration.Field.simulationConvergence.toString()));
			}
		}
		else {
			node.setStates(jsonConfiguration.optJSONArray(State.Field.states.toString()));
		}
		
		JSONObject jPercentiles = jsonConfiguration.optJSONObject(NodeConfiguration.Field.percentiles.toString());
		if (jPercentiles != null){
			Double lowerPercentile = jPercentiles.optDouble(NodeConfiguration.Percentiles.lowerPercentile.toString(), 25d);
			Double upperPercentile = jPercentiles.optDouble(NodeConfiguration.Percentiles.upperPercentile.toString(), 75d);
			node.setCustomPercentileSettings(lowerPercentile, upperPercentile);
		}
		
		if (jsonConfiguration.optBoolean(NodeConfiguration.Field.output.toString(), false)){
			en.setConnectableOutputNode(true);
		}
		
		try {
			if (jsonConfiguration.optBoolean(NodeConfiguration.Field.input.toString(), false)){
				en.setConnectableInputNode(true);
			}
		}
		catch (ExtendedBNException ex){
			throw new NodeException("Can't mark node as input node", ex);
		}
		
		// Create variables
		JSONArray jsonVariables = jsonConfiguration.optJSONArray(NodeConfiguration.Variables.variables.toString());
		if (jsonVariables != null){
			for(int i = 0; i < jsonVariables.length(); i++){
				JSONObject jsonVariable = jsonVariables.getJSONObject(i);
				String variableName = "";
				Double variableValue;
				try {
					try {
						variableName = jsonVariable.getString(NodeConfiguration.Variables.name.toString());
						variableValue = jsonVariable.getDouble(NodeConfiguration.Variables.value.toString());
					}
					catch(JSONException ex){
						throw new NodeException(ex.getMessage(), ex);
					}
					node.createVariable(variableName, variableValue);
				}
				catch (NodeException ex){
					if (!Advisory.addMessageIfLinked("Failed to create a Variable" + variableName + " in Node " + node.toStringExtra() + ": " + ex.getMessage())) {
						// If Advisory is linked, will load the rest of the model, otherwise stop with exception
						throw ex;
					}
				}
			}
		}
		
		if (withTables){
			JSONObject jsonTable = jsonConfiguration.optJSONObject(NodeConfiguration.Table.table.toString());
			node.setTable(jsonTable);
		}
		
		// Retrieve and store properties that are not used in the API but should persist through load/save
		
		if (jsonNode.has(Meta.Field.meta.toString())){
			node.jsonMeta = jsonNode.optJSONObject(Meta.Field.meta.toString());
		}
		
		if (jsonNode.has(Graphics.Field.graphics.toString())){
			node.jsonGraphics = jsonNode.optJSONObject(Graphics.Field.graphics.toString());
		}
		
		// Load Notes
		try {
			node.loadMetaNotes();
		}
		catch (JSONException ex){
			throw new NodeException("Failed loading model notes", ex);
		}
		
		// Load graphics
		try {
			boolean visible = jsonNode.optJSONObject(NodeGraphics.Field.graphics.toString()).optBoolean(NodeGraphics.Field.visible.toString(), true);
			node.getLogicNode().setVisible(visible);
		}
		catch(NullPointerException ex){
			// Ignore, node graphics not set
		}
		
		return node;
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
			getLogicNode().getNotes().addNote(name, text);
		}
		
		// Don't need to keep loaded notes in temp storage
		jsonMeta.remove(Meta.Field.notes.toString());
	}
	
	/**
	 * Sets the manual NPT according to columns provided.
	 * 
	 * @param columns 2D array where first dimension are the columns and second dimension are the cells
	 * 
	 * @throws NodeException if provided table size is wrong or Node does not allow manual NPT
	 */
	public void setTableColumns(double[][] columns) throws NodeException {
		// Set manual table
		
		for (int i = 0; i < columns.length; i++) {
			for (int j = 0; j < columns[i].length; j++) {
				if (columns[i].length != columns[0].length){
					throw new NodeException("Table is not a square matrix");
				}
			}
		}
		
		int sizeGiven = columns.length * columns[0].length;
		
		List<ExtendedNode> parentNodes = getParentExtendedNodes();
		
		AtomicInteger sizeExpected = new AtomicInteger(1);
		parentNodes.stream().forEachOrdered(n -> sizeExpected.set(sizeExpected.get() * n.getExtendedStates().size()));
		sizeExpected.set(sizeExpected.get() * getLogicNode().getExtendedStates().size());
		
		if (sizeExpected.get() != sizeGiven){
			throw new NodeException("Table has a wrong number of cells (expecting: "+ sizeExpected + ", given: "+sizeGiven+")");
		}
		
		try {
			getLogicNode().setNPT(columns, parentNodes);
		}
		catch (ArrayIndexOutOfBoundsException ex){
			throw new NodeException("Failed to set table, check parents", ex);
		}
		catch (ExtendedBNException ex){
			throw new NodeException("Failed to set table", ex);
		}
	}
	
	/**
	 * Sets the manual NPT according to rows provided.
	 * 
	 * @param rows 2D array where first dimension are the rows and second dimension are the cells
	 * 
	 * @throws NodeException if provided table size is wrong or Node does not allow manual NPT
	 */
	public void setTableRows(double[][] rows) throws NodeException {
		// Set manual table
		// Transpose arrays
		
		double[][] columns = NodeConfiguration.transposeMatrix(rows);
		
		setTableColumns(columns);
	}
	
	/**
	 * Resets the node NPT to a uniform table.
	 * 
	 * @return false if operation failed, true otherwise
	 */
	public final boolean setTableUniform(){
		try {
			float[][] nptCurrent = getLogicNode().getNPT();
			double[][] nptNew = new double[nptCurrent[0].length][nptCurrent.length];

			for (double[] row: nptNew){
				Arrays.fill(row, 1.0);
			}
			MathsHelper.normaliseMatrix(nptNew);
			getLogicNode().setNPT(nptNew, getParentExtendedNodes());
		}
		catch (Exception ex){
			Logger.printThrowableIfDebug(ex);
			return false;
		}
		return true;
	}
	
	/**
	 * Attempts to reset node's table
	 * 
	 * @throws NodeException upon failure
	 */
	public void resetTable() throws NodeException {
		try {
			getNetwork().getLogicNetwork().getConnBN().getNodeWithAltId(getId()).updateNPTSize();
		}
		catch(Exception ex){
			throw new NodeException("Failed to reset table for node " + toStringExtra(), ex);
		}
	}
	
	/**
	 * Replaces the Node's probability table with one specified in the given JSON.
	 * <br>
	 * This must be used after all parents states and incoming links had been created.
	 * 
	 * @param jsonTable configuration of the table in JSON format
	 * 
	 * @throws NodeException if:
	 * <br>
	 * ∙ table type does not match the rest of the table configuration
	 * <br>
	 * ∙ table is not a square matrix
	 * <br>
	 * ∙ the number of cells does not match the number of partitions created by the combination of parent states
	 */
	public void setTable(JSONObject jsonTable) throws NodeException {
		if (jsonTable == null || jsonTable.length() == 0){
			return;
		}
		
		List<String> allowedTokens = getAllowedFunctionTokens();
		
		try {
			String tableType = jsonTable.getString(NodeConfiguration.Table.type.toString());

			if (jsonTable.has(NodeConfiguration.Table.probabilities.toString())){
				// Restore cached NPT if available
				try {
					double[][] npt = NodeConfiguration.extractNPTColumns(jsonTable.getJSONArray(NodeConfiguration.Table.probabilities.toString()));
					if (NodeConfiguration.Table.column.toString().equals(jsonTable.optString(NodeConfiguration.Table.pvalues.toString()))){
						// The probability values were read from XML where they have been defined as columns rather than rows, need to transpose
						npt = NodeConfiguration.transposeMatrix(npt);
					}
					List<ExtendedNode> parentNodes = getParents().stream().filter(n -> n.getNetwork().equals(getNetwork())).map(node -> node.getLogicNode()).collect(Collectors.toList());
					getLogicNode().setNPT(npt, parentNodes);
				}
				catch (ExtendedBNException ex){
					getLogicNode().setNptReCalcRequired(true);
					resetTable();
					if (!tableType.equalsIgnoreCase(NodeConfiguration.TableType.Manual.toString())){
						// NPT failed to set - corrupted?						
						// Node is not manual - we can afford to lose the NPT
						Advisory.addMessageIfLinked("Node " + toStringExtra() + " underlying table was corrupted and will need to be recalculated");
					}
					else if (ex.getCause() instanceof ArrayIndexOutOfBoundsException){
						throw new NodeException("NPT size is wrong", ex);
					}
					else {
						throw new NodeException("Failed to extract NPT", ex);
					}
				}
				catch (ArrayIndexOutOfBoundsException ex){
					getLogicNode().setNptReCalcRequired(true);
					resetTable();
					throw new NodeException("NPT size is wrong", ex);
				}
			}
			
			if(tableType.equalsIgnoreCase(NodeConfiguration.TableType.Manual.toString())){
				if (this.isSimulated()){
					if (!Advisory.addMessageIfLinked("Node " + toStringExtra() + " is simulated, but the table data had a Manual table type. We recommend to check the expressions in this node.")) {
						// If Advisory is linked, the invalid table will be applied as is
						throw new NodeException("Can't set a manual NPT for a simulated node");
					}
				}
			}
			else if (tableType.equalsIgnoreCase(NodeConfiguration.TableType.Expression.toString())){
				String expression = jsonTable.getJSONArray(NodeConfiguration.Table.expressions.toString()).getString(0);
				
				try {
					setTableFunction(expression, allowedTokens);
				}
				catch (NodeException ex){
					if (Advisory.getCurrentThreadGroup() != null){
						// Expression is not valid even with relaxed tokens - was this edited by hand into something invalid?
						Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage("Failed parsing functions for node " + toStringExtra()));
					}
					else {
						throw ex;
					}
				}
			}
			else if (tableType.equalsIgnoreCase(NodeConfiguration.TableType.Partitioned.toString())){
				try {
					List<String> partitionParentIDs = JSONUtils.toList(jsonTable.getJSONArray(NodeConfiguration.Table.partitions.toString()), String.class);
					List<Node> partitionParents = partitionParentIDs.stream().map(id -> getNetwork().getNode(id)).collect(Collectors.toList());
					List<String> expressions = JSONUtils.toList(jsonTable.getJSONArray(NodeConfiguration.Table.expressions.toString()), String.class);
					setTableFunctions(expressions, allowedTokens, false, partitionParents);
				}
				catch(JSONException ex){
					if (Advisory.getCurrentThreadGroup() != null){
						Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage("Partitioned table definition missing or corrupt in node " + toStringExtra(), ex));
					}
					else {
						throw ex;
					}
				}
			}
			else {
				throw new NodeException("Invalid table type");
			}
		}
		catch (JSONException ex){
			throw new NodeException(JSONUtils.createMissingAttrMessage(ex), ex);
		}
	}
	
	/**
	 * Sets Node function to the one provided. Will use node parent IDs and expression variables as allowed tokens.
	 * <br>
	 * Resets Node table partitioning and sets Table type to NodeConfiguration.TableType.Expression.
	 * 
	 * @param expression function to set
	 * 
	 * @throws NodeException if function is invalid
	 */
	public void setTableFunction(String expression) throws NodeException {
		setTableFunctions(Arrays.asList(expression), getAllowedFunctionTokens(), false, null);
	}
	
	/**
	 * Returns a list of parent IDs, variable names etc to use as parseable function tokens.
	 * 
	 * @return list of allowed function tokens for this node
	 */
	public List<String> getAllowedFunctionTokens(){
		List<String> allowedTokens = new ArrayList<>();
		
		List<String> parentIDs = getParents().stream().filter(n -> n.getNetwork().equals(getNetwork())).map(node -> node.getId()).collect(Collectors.toList());
		List<String> variableNames = getLogicNode().getExpressionVariables().getAllVariableNames();
		
		allowedTokens.addAll(parentIDs);
		allowedTokens.addAll(variableNames);
		
		return allowedTokens;
	}
	
	/**
	 * Sets Node function to the one provided.
	 * <br>
	 * Resets Node table partitioning and sets Table type to NodeConfiguration.TableType.Expression.
	 * 
	 * @param expression function to set
	 * @param allowedTokens list of parent IDs, variable names etc to add as parseable tokens; if null, all tokens are allowed
	 * 
	 * @throws NodeException if function is invalid
	 */
	public void setTableFunction(String expression, List<String> allowedTokens) throws NodeException {
		setTableFunctions(Arrays.asList(expression), allowedTokens, false, null);
	}
	
	/**
	 * Sets Node function to the one provided.
	 * <br>
	 * Resets Node table partitioning and sets Table type to NodeConfiguration.TableType.Expression.
	 * 
	 * @param expression function to set
	 * @param allowedTokens list of parent IDs, variable names etc to add as parseable tokens; if null, all tokens are allowed
	 * @param relaxFunctionRequirements if true, all function requirements are lifted and any number of arguments are allowed
	 * 
	 * @throws NodeException if function is invalid
	 */
	protected void setTableFunction(String expression, List<String> allowedTokens, boolean relaxFunctionRequirements) throws NodeException {
		setTableFunctions(Arrays.asList(expression), allowedTokens, relaxFunctionRequirements, null);
	}
	
	/**
	 * Parses the provided expression as an ExtendedNodeFunction
	 * 
	 * @param expression function to set
	 * @param allowedTokens list of parent IDs, variable names etc to add as parseable tokens; if null, all tokens are allowed
	 * @param relaxFunctionRequirements if true, all function requirements are lifted and any number of arguments are allowed
	 * 
	 * @return ExtendedNodeFunction parsed from the provided expression
	 */
	protected ExtendedNodeFunction parseAsFunction(String expression, List<String> allowedTokens, boolean relaxFunctionRequirements){
		
		if (Advisory.getCurrentThreadGroup() != null && expression.contains("?")){
			expression = expression.replace("?", "");
			Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage("Functions for node " + toStringExtra() + " contained invalid characters and were cleaned. We recommend to check the expressions in this node."));
		}
		
		ExtendedNodeFunction enf;
		try {
			try {
				enf = ExpressionParser.parseFunctionFromString(expression, allowedTokens, relaxFunctionRequirements);
				for(String fname: ExpressionParser.parsed_functions){
					// Restore function names to full versions with spaces
					if (fname.replaceAll(" ", "").equalsIgnoreCase(enf.getName())){
						enf.setName(fname);
					}
				}
				return enf;
			}
			catch (ParseException ex){
				if (Advisory.getCurrentThreadGroup() != null){
					Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage("Functions for node " + toStringExtra() + " contain invalid tokens. We recommend to check the expressions in this node.", ex));
					enf = ExpressionParser.parseFunctionFromString(expression, null, true);
					for(String fname: ExpressionParser.parsed_functions){
						// Restore function names to full versions with spaces
						if (fname.replaceAll(" ", "").equalsIgnoreCase(enf.getName())){
							enf.setName(fname);
						}
					}
					return enf;
				}
				else {
					throw ex;
				}
			}
		}
		catch (ParseException ex){
			throw new NodeException("Unable to parse node function `"+expression+"`", ex);
		}
	}
	
	/**
	 * Sets Node expressions to the ones provided.
	 * <br>
	 * Table type will be set to NodeConfiguration.TableType.Partitioned or NodeConfiguration.TableType.Function depending on how many expressions were provided.
	 * <br>
	 * If the node only has one non-simulated parent, this parent will be automatically used to partition this node. Otherwise partitions must have been set beforehand using {@link #partitionByParents(java.util.List)}
	 * 
	 * @param expressions expressions to set
	 * @param partitionParents discrete parents to partition by
	 * 
	 * @throws NodeException if a function is invalid
	 */
	public void setTableFunctions(List<String> expressions, List<Node> partitionParents) throws NodeException {
		setTableFunctions(expressions, getAllowedFunctionTokens(), false, partitionParents);
	}
	
	/**
	 * Sets Node expressions to the ones provided.
	 * <br>
	 * Table type will be set to NodeConfiguration.TableType.Partitioned or NodeConfiguration.TableType.Function depending on how many expressions were provided.
	 * 
	 * @param expressions expressions to set
	 * @param allowedTokens list of parent IDs, variable names etc to add as parseable tokens; if null, all tokens are allowed
	 * @param relaxFunctionRequirements if true, all function requirements are lifted and any number of arguments are allowed
	 * @param partitionParents discrete parents to partition by, ignored if null or empty
	 * 
	 * @throws NodeException if a function is invalid
	 */
	protected void setTableFunctions(List<String> expressions, List<String> allowedTokens, boolean relaxFunctionRequirements, List<Node> partitionParents) throws NodeException {
		
		if (expressions.isEmpty()){
			return;
		}
		
		List<ExtendedNodeFunction> functions = expressions.stream()
				.map(expression -> parseAsFunction(expression, allowedTokens, relaxFunctionRequirements))
				.collect(Collectors.toList());
		
		if (functions.size() == 1){
			getLogicNode().setExpression(functions.get(0));
			return;
		}
		
		if (functions.size() > 1){
			
			if (partitionParents != null && !partitionParents.isEmpty()){
				partitionByParents(partitionParents);
			}
			getLogicNode().setPartitionedExpressions(functions);
		}
	}
	
	/**
	 * Sets node partitions based on the parent states.
	 * <br>
	 * Order of partitions will be based on the order of nodes in the array and states within them.
	 * 
	 * @param partitionParents parents to partition by
	 * 
	 * @throws NodeException if one of the nodes in partitionParents is simulated or is not a parent of this Node
	 */
	public void partitionByParents(List<Node> partitionParents) throws NodeException {
		getLogicNode().setPartitionedExpressionModelNodes(partitionParents.stream().map(node -> node.getLogicNode()).collect(Collectors.toList()));
	}
	
	/**
	 * Replaces Node's states by the ones given in the array.
	 * <br>
	 * This action resets the probability table to uniform.
	 * 
	 * @param states new Node's states
	 * 
	 * @throws NodeException if state is an invalid range; or if the Node is simulated
	 */
	public void setStates(String[] states) throws NodeException{
		setStates(Arrays.asList(states));
	}
	
	/**
	 * Replaces Node's states by the ones given in the array.
	 * <br>
	 * This action resets the probability table to uniform.
	 * 
	 * @param states new Node's states
	 * 
	 * @throws NodeException if state is an invalid range; or if the Node is simulated
	 */
	public void setStates(List<String> states) throws NodeException{
		if (isSimulated()){
			throw new NodeException("Can't set states for a simulated node");
		}
		
		ExtendedNode en = getLogicNode();
		
		DataSet ds = new DataSet();
		
		for(int s = 0; s < states.size(); s++){
			
			String stateName = states.get(s).trim();
			
			if (stateName.isEmpty()){
				throw new NodeException("State name can't be an empty string");
			}
			
			if (en instanceof RankedEN){
				ds.addLabelledDataPoint(stateName);
			}
			else if (en instanceof NumericalEN){

				double lowerBound = Double.NEGATIVE_INFINITY;
				double upperBound = Double.POSITIVE_INFINITY;
				Range range;
				
				if (stateName.contains(" - ")){
					// State is a range
					String[] parts = stateName.split(" - ");
					lowerBound = Double.parseDouble(parts[0]);
					upperBound = Double.parseDouble(parts[1]);
				}
				else {
					// Not a range, same bounds
					lowerBound = Double.parseDouble(stateName);
					upperBound = lowerBound;
				}
				
				try {
					range = new Range(lowerBound, upperBound);
				}
				catch (MinervaRangeException ex){
					throw new NodeException("Invalid state range in `"+stateName+"`", ex);
				}
				
				NameDescription nd = new NameDescription(stateName, stateName);
				IntervalDataPoint dp = new IntervalDataPoint();
				dp.setLabel(nd.getShortDescription());
				dp.setValue(range.midPoint());
				dp.setIntervalLowerBound(range .getLowerBound());
				dp.setIntervalUpperBound(range .getUpperBound());
				ds.addDataPoint(dp);
			}
			else {
				ds.addLabelledDataPoint(stateName);
			}
		}
		
		try {
			en.createExtendedStates(ds);
		}
		catch (ExtendedStateNumberingException | ExtendedStateException ex){
			throw new NodeException("Failed to parse states", ex);
		}
	}
	
	/**
	 * Replaces Node's states by the ones given in the JSON array.
	 * <br>
	 * If null or empty array are provided, no changes are made.
	 * <br>
	 * This action resets the probability table to uniform.
	 * 
	 * @param jsonStates new Node's states
	 * 
	 * @throws NodeException if state is an invalid range; or if the Node is simulated
	 */
	public void setStates(JSONArray jsonStates) throws NodeException{
		
		if (jsonStates == null || jsonStates.length() == 0){
			return;
		}
		
		String[] states = new String[jsonStates.length()];
		for(int s = 0; s < jsonStates.length(); s++){
			states[s] = jsonStates.optString(s,"");
		}
		setStates(states);
	}
	
	/**
	 * Enables calculation simulation for the Node (only possible for ContinuousInterval or IntegerInterval nodes).
	 * <br>
	 * All current states will be replaced by dynamic states.
	 * 
	 * @return true if action was performed, false if no action was permitted or required
	 */
	public boolean convertToSimulated() throws NodeException {
		boolean canSimulate = getLogicNode() instanceof ContinuousEN && !(getLogicNode() instanceof RankedEN);
		
		if (!canSimulate || isSimulated()){
			return false;
		}
		
		NodeConfiguration.setDefaultIntervalStates(this);
		ContinuousEN cien = (ContinuousEN)this.getLogicNode();
		cien.setSimulationNode(true);
		if (cien.getExpression() == null) {
			this.setTableFunction("Arithmetic(0)");
		}
		
		return true;
	}
	
	/**
	 * Disables simulation and converts dynamic states from the results of the provided DataSet into static permanent states.<br>
	 * Any VariableObservations in the DataSet will replace current Node variable defaults and will be replaced from the DataSet observations.
	 * 
	 * @param dataSet DataSet to use for creating static states from results
	 * 
	 * @return true if action was performed, false if no action was permitted or required
	 * 
	 * @throws NodeException if states could not be created from the DataSet results
	 */
	public boolean convertToStatic(com.agenarisk.api.model.DataSet dataSet) throws NodeException {
		
		boolean canSimulate = getLogicNode() instanceof ContinuousEN && !(getLogicNode() instanceof RankedEN);
		
		if (!canSimulate || !isSimulated()){
			return false;
		}
		
		ContinuousEN cien = (ContinuousEN)this.getLogicNode();
		
		int dsIndex = dataSet.getDataSetIndex();
		
		MarginalDataItemList mdil = getNetwork().getModel().getLogicModel().getMarginalDataStore().getMarginalDataItemListForNode(getNetwork().getLogicNetwork(), getLogicNode());
		if (mdil.getMarginalDataItems().isEmpty()) {
			throw new NodeException("Model missing calculation results");
		}
		
		if (mdil.getMarginalDataItems().size() < dsIndex + 1){
			throw new NodeException("Model missing calculation results for the Data Set " + dataSet.getId());
		}
		
		// Backup observation to restore after static conversion
		// This is needed because states will be regenerated which clears any previous observations
		JSONObject jObservation = null;
		if (dataSet.hasObservation(this)){
			jObservation = dataSet.getObservation(this).toJson();
		}
	
		try {
			DataSet ds = getNetwork().getModel().getLogicModel().getMarginalDataStore().getMarginalDataItemListForNode(getNetwork().getLogicNetwork(), getLogicNode()).getMarginalDataItemAtIndex(dsIndex).getDataset();
			ContinuousEN.ConvertToNonSimulation(cien, ds, getNetwork().getLogicNetwork(), dataSet.getLogicScenario());
			for (VariableObservation vo: dataSet.getVariableObservations(this)){
				String voName = vo.getVariableName();
				double varVal = vo.getVariableValue();
				VariableList logicVarList = getLogicNode().getExpressionVariables();
				uk.co.agena.minerva.util.model.Variable logicVar = logicVarList.getVariable(voName);
				logicVarList.updateVariable(logicVar, voName, varVal);
			};
		}
		catch (ExtendedStateException | ExtendedStateNumberingException | NullPointerException | IndexOutOfBoundsException | MinervaVariableException ex){
			throw new NodeException("Failed to retrieve calculation result from Data Set", ex);
		}
		
		// Restore observation from JSON
		if (jObservation != null){
			dataSet.setObservation(jObservation);
		}
		
		return true;
	}
	
	/**
	 * Returns true if the Node is simulated and false otherwise.
	 * 
	 * @return true if the Node is simulated and false otherwise
	 */
	public boolean isSimulated(){
		if (getLogicNode() instanceof ContinuousEN && !(getLogicNode() instanceof RankedEN)){
			return ((ContinuousEN)getLogicNode()).isSimulationNode();
		}
		return false;
	}
	
	/**
	 * Returns true if the Node is continuous or integer interval
	 * 
	 * @return true if the Node is continuous or integer interval
	 */
	public boolean isNumericInterval(){
		return Type.ContinuousInterval.equals(getType()) || Type.IntegerInterval.equals(getType());
	}
	
	/**
	 * Returns JSON equivalent of this Node as a String.
	 * 
	 * @return String version of this Node
	 */
	@Override
	public String toString(){
		return toJson().toString();
	}
	
	/**
	 * Returns `network`.`node` String representing this Node.
	 * 
	 * @return detailed String representing this Node
	 */
	public String toStringExtra(){
		return "`"+getNetwork().getName()+" ("+getNetwork().getId()+")`.`"+getName()+" ("+getId()+")`";
	}

	/**
	 * Returns the Network containing this Node.
	 * <br>
	 * Using logic objects directly is <b>unsafe</b> and is likely to break something.
	 * 
	 * @return the Network containing this Node
	 */
	public Network getNetwork() {
		return network;
	}

	/**
	 * Returns the underlying ExtendedNode.
	 * 
	 * @return the underlying ExtendedNode
	 */
	public ExtendedNode getLogicNode() {
		return logicNode;
	}

	/**
	 * Gets the ID of this Node.
	 * 
	 * @return the ID of this Node
	 */
	@Override
	public String getId() {
		return getLogicNode().getConnNodeId();
	}

	/**
	 * Changes the ID of this Node to the provided ID, if the new ID is not already taken.
	 * <br>
	 * Will lock IDContainer.class while doing so.
	 * 
	 * @param newId the new ID
	 * 
	 * @throws NodeException if fails to change ID
	 */
	@Override
	public void setId(String newId) throws NodeException {
		String oldId = getId();
		String errorMessage = "Failed to change ID of Node from `" + oldId + "` to `" + newId + "`";
		
		try {
			getNetwork().changeContainedId(this, newId);
			getNetwork().getLogicNetwork().updateConnNodeId(getLogicNode(), newId);
		}
		catch (CoreBNNodeNotFoundException | ExtendedBNException ex){
			try {
				// Rollback
				getNetwork().changeContainedId(this, oldId);
			}
			catch (NetworkException ex2){
				throw new NodeException(errorMessage, ex2);
			}
			throw new NodeException(errorMessage, ex);
		}
		catch (NetworkException ex){
			throw new NodeException(errorMessage, ex);
		}
	}
	
	/**
	 * Sets the name of this Node.
	 * 
	 * @param name new name
	 */
	@Override
	public void setName(String name){
		getLogicNode().getName().setShortDescription(name);
	}
	
	/**
	 * Gets the name of this node.
	 * 
	 * @return the name of this node
	 */
	@Override
	public String getName(){
		return getLogicNode().getName().getShortDescription();
	}
	
	/**
	 * Sets the description of this Node.
	 * 
	 * @param description new description
	 */
	@Override
	public void setDescription(String description){
		getLogicNode().getName().setLongDescription(description);
	}
	
	/**
	 * Gets the description of this node
	 * 
	 * @return the description of this node
	 */
	@Override
	public String getDescription(){
		return getLogicNode().getName().getLongDescription();
	}

	/**
	 * Compares this Node object to another based on the Id of this object.
	 * 
	 * @param o another Node object
	 * 
	 * @return a negative integer, zero, or a positive integer if the value of this object's ID precedes the one of the specified object's ID
	 */
	@Override
	public int compareTo(Node o) {
		return new Id(getId()).compareTo(new Id(o.getId()));
	}
	
	/**
	 * Checks equality of a given object to this Node. Returns true if logic nodes of both objects are the same.
	 * 
	 * @param obj The object to compare this Node against
	 * 
	 * @return true if the given object represents the same Node as this Node, false otherwise
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Node)){
			return false;
		}
		
		return this.getLogicNode() == ((Node)obj).getLogicNode();
	}

	/**
	 * Returns a hash code value for this object.
	 * 
	 * @return a hash code value for this object.
	 */
	@Override
	public int hashCode() {
		return System.identityHashCode(getLogicNode());
	}
	
	/**
	 * Removes a link between this Node and the linkedNode, if such link exists.
	 * <br>
	 * Will check incoming and outgoing Links
	 * <br>
	 * Both Nodes will no longer refer to each other
	 * 
	 * @param linkedNode the Node to break Link with
	 * 
	 * @return false if there was no Link; or true if the Link was removed
	 */
	@Override
	public boolean unlink(Node linkedNode){
		return Node.unlinkNodes(this, linkedNode);
	}
	
	/**
	 * Creates a JSON representing this Node, ready for file storage.
	 * 
	 * @return JSONObject representing this Node
	 */
	@Override
	public JSONObject toJson() {
		JSONObject json = JSONAdapter.toJSONObject(logicNode);
		json.put(Graphics.Field.graphics.toString(), jsonGraphics);
		json.put(Meta.Field.meta.toString(), jsonMeta);
		return json;
	}
	
	public JSONObject getGraphicsJson(){
		return jsonGraphics;
	}
	
	/**
	 * Find a state by given label in this Node's underlying logic node.
	 * 
	 * @param label label of the required state
	 * 
	 * @return state with given label or null if such state does not exist
	 */
	public State getState(String label) {
		return State.getState(this, label);
	}
	
	/**
	 * Returns a representation of Node's states at the time of the request.<br>
	 * The list will not be updated to reflect any changes, and no changes to the list or the States will be reflected in the Model.
	 * 
	 * @return representation of Node's states
	 */
	public List<State> getStates(){
		return ((List<ExtendedState>) getLogicNode().getExtendedStates()).stream().map(es -> State.getState(this, es.getName().getShortDescription())).collect(Collectors.toList());
	}
	
	/**
	 * Gets the Type of this Node.
	 * 
	 * @return Type of this Node
	 */
	public Type getType(){
		ExtendedNode en = getLogicNode();
		return NodeConfiguration.resolveNodeType(en);
	}

	/**
	 * Links this Node to an underlying Minerva Node object. Should only be used while wrapping a new Model around the Minerva Model.
	 * 
	 * @param logicNode the logical node
	 */
	protected void setLogicNode(ExtendedNode logicNode) {
		if (!new Id(getId()).equals(new Id(logicNode.getConnNodeId()))){
			throw new AgenaRiskRuntimeException("Logic node id mismatch: " + getId() + "," + logicNode.getConnNodeId());
		}
		
		this.logicNode = logicNode;
	}
	
	/**
	 * Builds and returns a list of ExtendedNode parents.
	 * 
	 * @return list of ExtendedNode parents
	 */
	private List<ExtendedNode> getParentExtendedNodes(){
		return getParents().stream().map(node -> node.getLogicNode()).collect(Collectors.toList());
	}
	
	
	/**
	 * Creates default states as appropriate for this Node's Type
	 */
	private void createDefaultStates(){
		
		switch(getType()){
			case ContinuousInterval:
			case IntegerInterval:
				setStates(new String[]{
					"-Infinity - -1",
					"-1 - 1",
					"1 - Infinity"
				});
				setTableRows(new double[][]{
					{1},
					{1},
					{1}
				});
				
			default:
		}
	}
	
	/**
	 * Returns the type of the table configured for the Node.<br>
	 * Possible values are:<br>
	 * • Manual<br>
	 * • Expression<br>
	 * • Partitioned
	 * 
	 * @return table type of the Node
	 */
	public NodeConfiguration.TableType getTableType(){
		switch(logicNode.getFunctionMode()){
			default:
			case ExtendedNode.EDITABLE_NPT:
				return NodeConfiguration.TableType.Manual;
				
			case ExtendedNode.EDITABLE_NODE_FUNCTION:
				return NodeConfiguration.TableType.Expression;
				
			case ExtendedNode.EDITABLE_PARENT_STATE_FUNCTIONS:
				return NodeConfiguration.TableType.Partitioned;
		}
	}
	
	/**
	 * Returns true if this Node has an incoming link from another Network.
	 * 
	 * @return true if this Node has an incoming link from another Network
	 */
	public boolean isConnectedInput(){
		return getLinksIn().stream().anyMatch(link -> {
			return !Objects.equals(getNetwork(),link.getFromNode().getNetwork());
		});
	}
	
	/**
	 * Returns true if this Node has an outgoing link to another Network.
	 * 
	 * @return true if this Node has an outgoing link to another Network
	 */
	public boolean isConnectedOutput(){
		return getLinksOut().stream().anyMatch(link -> {
			return !Objects.equals(getNetwork(),link.getToNode().getNetwork());
		});
	}
	
	/**
	 * Builds and returns a set of ancestors for this Node.<br>
	 * Does not include itself.<br>
	 * Does not follow cross-network links (only includes Nodes in the same Network).
	 * 
	 * @return HashSet of ancestors for this Node
	 */
	public Set<Node> getAncestors(){
		Set set = new LinkedHashSet();
		getParents().stream().filter(n -> Objects.equals(getNetwork(), n.getNetwork())).forEach(n -> {
			set.add(n);
			set.addAll(n.getAncestors());
		});
		return set;
	}
	
	/**
	 * Builds and returns a set of descendants for this Node.<br>
	 * Does not include itself.<br>
	 * Does not follow cross-network links (only includes Nodes in the same Network).
	 * 
	 * @return HashSet of descendants for this Node
	 */
	public Set<Node> getDescendants(){
		Set set = new LinkedHashSet();
		getChildren().stream().filter(n -> Objects.equals(getNetwork(), n.getNetwork())).forEach(n -> {
			set.add(n);
			set.addAll(n.getDescendants());
		});
		return set;
	}
	
	/**
	 * Creates a new node Variable.
	 * 
	 * @param varName unique Variable name, only number, letters and underscores are allowed, but can't be just a number
	 * @param defaultValue default value of the Variable
	 * 
	 * @return the Variable instance created
	 * 
	 * @throws NodeException if a Variable with this name already exists in this Node
	 */
	public synchronized Variable createVariable(String varName, double defaultValue) throws NetworkException {
		try {
			uk.co.agena.minerva.util.model.Variable logicVariable = getLogicNode().addExpressionVariable(varName, defaultValue, true);
			Variable variable = new Variable(this, logicVariable);
			variablesCache.put(varName, variable);
			return variable;
		}
		catch (ExtendedBNException ex){
			throw new NodeException(ex.getMessage(), ex);
		}
	}
	
	/**
	 * Retrieves and returns the Variable by name.
	 * 
	 * @param varName name of the Variable to return
	 * 
	 * @return Variable with provided name or null
	 */
	public synchronized Variable getVariable(String varName){
		if (variablesCache.containsKey(varName)){
			return variablesCache.get(varName);
		}
		try {
			VariableList logicVarList = getLogicNode().getExpressionVariables();
			uk.co.agena.minerva.util.model.Variable logicVar = logicVarList.getVariable(varName);
			Variable variable = new Variable(this, logicVar);
			variablesCache.put(varName, variable);
			return variable;
		}
		catch (Exception ex){
			// Actually does not exist
			return null;
		}
	}
	
	/**
	 * Removes Variable by name if it exists
	 * 
	 * @param varName name of the Variable to remove
	 */
	public synchronized void removeVariable(String varName) {
		try {
			VariableList logicVarList = getLogicNode().getExpressionVariables();
			uk.co.agena.minerva.util.model.Variable logicVar = logicVarList.getVariable(varName);
			logicVarList.removeVariable(logicVar);
			variablesCache.remove(varName);
		}
		catch (Exception ex){
			// It throws exception if no variable exists, which we don't really care about
			return;
		}
	}
	
	/**
	 * Gets an unmodifiable list of all Variables.<br>
	 * Will not reflect Variables being added or deleted from the Node.
	 * 
	 * @return unmodifiable list of all Variables
	 */
	public synchronized List<Variable> getVariables(){
		VariableList logicVarList = getLogicNode().getExpressionVariables();
		List<Variable> list = ((List<String>)logicVarList.getAllVariableNames()).stream().map(varName -> getVariable(varName)).collect(Collectors.toList());
		return Collections.unmodifiableList(list);
	}
	
	public boolean setCustomPercentileSettings(double lowerPercentile, double upperPercentile){
		if (!(logicNode instanceof ContinuousEN)){
			return false;
		}
		
		ContinuousEN cien = (ContinuousEN) logicNode;
		cien.setPercentileSettingsOnNodeForScenario(null, lowerPercentile, upperPercentile);
		
		return true;
	}
	
	public double getLowerPercentileSetting(){
		try {
			if (logicNode instanceof ContinuousEN){
				return (Double)((ContinuousEN)logicNode).getPercentileSettingsOnNodeForScenario(null).get(1);
			}
		}
		catch (Exception ex){}
		return 25d;
	}
	
	public double getUpperPercentileSetting(){
		try {
			if (logicNode instanceof ContinuousEN){
				return (Double)((ContinuousEN)logicNode).getPercentileSettingsOnNodeForScenario(null).get(2);
			}
		}
		catch (Exception ex){}
		return 75d;
	}

}
