package com.agenarisk.api.model;

import com.agenarisk.api.model.interfaces.Named;
import com.agenarisk.api.model.interfaces.Networked;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.JSONUtils;
import com.singularsys.jep.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.corebn.CoreBNNodeNotFoundException;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateException;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNumberingException;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.NumericalEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.model.field.Id;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.NameDescription;
import uk.co.agena.minerva.util.model.Range;
import uk.co.agena.minerva.util.nptgenerator.ExpressionParser;

/**
 * Node class represents an equivalent to a Node in AgenaRisk Desktop or ExtendedBN in AgenaRisk Java API v1.
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
	private JSONObject jsonGraphics;
	
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
	}
	
	/**
	 * Returns a copy of the list of incoming Links. The membership is only guaranteed to be valid at the time of request and is not maintained.
	 * 
	 * @return list of incoming Links
	 */
	@Override
	public List<Link> getLinksIn() {
		List<Link> list;
		synchronized(linksIn) {
			list = new ArrayList<>(linksIn);
		}
		return list;
	}

	/**
	 * Returns a copy of the list of outgoing Links. The membership is only guaranteed to be valid at the time of request and is not maintained.
	 * 
	 * @return list of outgoing Links
	 */
	@Override
	public List<Link> getLinksOut() {
		List<Link> list;
		synchronized(linksOut) {
			list = new ArrayList<>(linksOut);
		}
		return list;
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
	 * @deprecated For internal use only
	 */
	@Deprecated
	protected boolean addLink(Link link){
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
	 * @deprecated For internal use only
	 */
	@Deprecated
	protected boolean removeLink(Link link){
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
	 * Does nothing if json is null or empty.
	 * 
	 * @param model the model to create Links in
	 * @param jsonLinks configuration of the Links
	 * 
	 * @throws JSONException if JSON structure is invalid or inconsistent
	 * @throws LinkException if a Link fails to be created
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
				Environment.logIfDebug("No cross network link type provided, defaulting to type = Marginals");
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
				
				if (!Objects.equals(fromNode.getType(), toNode.getType())){
					throw new LinkException("Cross network link can only be created between nodes of the same type (" + fromNode + " is " + fromNode.getType() + ", " + toNode + " is " + toNode.getType() + ")");
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
			
//			if(true)throw new UnsupportedOperationException("If the link was created, for same-network links we need to reapply child's NPT");
			
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
	 * Note: this <b>does not</b> load node's table from JSON. Instead, use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
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
		String id = jsonNode.getString(Field.id.toString());
		String name = jsonNode.optString(Field.name.toString());
		String description = jsonNode.optString(Field.description.toString());
		
		if (name.isEmpty()){
			name = id;
		}
		
		JSONObject jsonConfiguration = jsonNode.getJSONObject(NodeConfiguration.Field.configuration.toString());
		String typeString = jsonConfiguration.getString(NodeConfiguration.Field.type.toString());
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
				String variableName = jsonVariable.getString(NodeConfiguration.Variables.name.toString());
				Double variableValue = jsonVariable.getDouble(NodeConfiguration.Variables.value.toString());
				try {
					uk.co.agena.minerva.util.model.Variable variable = en.addExpressionVariable(variableName, variableValue, true);
				}
				catch (ExtendedBNException ex){
					throw new NodeException("Duplicate variable names detected", ex);
				}
			}
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
			String text = jsonNote.getString(Meta.Field.name.toString());
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
		
		List<ExtendedNode> parentNodes = getParents().stream().map(node -> node.getLogicNode()).collect(Collectors.toList());
		
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
		if (jsonTable.length() == 0){
			return;
		}
		
		List<String> allowedTokens = new ArrayList<>();
		
		List<String> parentIDs = getParents().stream().filter(n -> n.getNetwork().equals(getNetwork())).map(node -> node.getId()).collect(Collectors.toList());
		List<String> variableNames = getLogicNode().getExpressionVariables().getAllVariableNames();
		
		allowedTokens.addAll(parentIDs);
		allowedTokens.addAll(variableNames);
		
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
					throw new NodeException("Failed to extract NPT", ex);
				}
				catch (ArrayIndexOutOfBoundsException ex){
					throw new NodeException("NPT may not be a square matrix", ex);
				}
			}
			
			if(tableType.equalsIgnoreCase(NodeConfiguration.TableType.Manual.toString())){
				if (this.isSimulated()){
					throw new NodeException("Can't set a manual NPT for a simulated node");
				}
			}
			else if (tableType.equalsIgnoreCase(NodeConfiguration.Table.expression.toString())){
				String expression = jsonTable.getJSONArray(NodeConfiguration.Table.expressions.toString()).getString(0);
				setTableFunction(expression, allowedTokens);
			}
			else if (tableType.equalsIgnoreCase(NodeConfiguration.TableType.Partitioned.toString())){
				// Get parents used for partitioning (can be only a subset of all parents)
				List<String> partitionParentIDs = JSONUtils.toList(jsonTable.getJSONArray(NodeConfiguration.Table.partitions.toString()), String.class);
				List<ExtendedNode> partitionParentNodes = new ArrayList<>();
				partitionParentIDs.stream().forEachOrdered(parentID -> {
					ExtendedNode parent = getNetwork().getLogicNetwork().getExtendedNodeWithUniqueIdentifier(parentID);
					if (parent == null){
						throw new IllegalArgumentException("No such parent `"+parentID+"` found");
					}
					partitionParentNodes.add(parent);
				});
				getLogicNode().setPartitionedExpressionModelNodes(partitionParentNodes);

				// Create functions
				List<ExtendedNodeFunction> enfs = new ArrayList<>();
				List<String> expressions = JSONUtils.toList(jsonTable.getJSONArray(NodeConfiguration.Table.expressions.toString()), String.class);

				for(String expression: expressions){
					ExtendedNodeFunction enf;
					try {
						enf = ExpressionParser.parseFunctionFromString(expression, parentIDs);
					}
					catch (ParseException ex){
						throw new JSONException("Unable to parse node function `"+expression, ex);
					}
					enfs.add(enf);
				}

				getLogicNode().setPartitionedExpressions(enfs);

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
	 * Sets Node function to the one provided.
	 * <br>
	 * Resets Node table partitioning and sets Table type to NodeConfiguration.TableType.Expression.
	 * 
	 * @param expression function to set
	 * 
	 * @throws NodeException if function is invalid
	 */
	public void setTableFunction(String expression) throws NodeException {
		setTableFunction(expression, new ArrayList<>());
	}
	
	/**
	 * Sets Node function to the one provided.
	 * <br>
	 * Resets Node table partitioning and sets Table type to NodeConfiguration.TableType.Expression.
	 * 
	 * @param expression function to set
	 * @param allowedTokens list of parent IDs, variable names etc to add as parseable tokens
	 * 
	 * @throws NodeException if function is invalid
	 */
	public void setTableFunction(String expression, List<String> allowedTokens) throws NodeException {
		ExtendedNodeFunction enf;
		try {
			enf = ExpressionParser.parseFunctionFromString(expression, allowedTokens);
			for(String fname: ExpressionParser.parsed_functions){
				// Restore function names to full versions with spaces
				if (fname.replaceAll(" ", "").equalsIgnoreCase(enf.getName())){
					enf.setName(fname);
				}
			}
		}
		catch (ParseException ex){
			throw new NodeException("Unable to parse node function `"+expression+"`", ex);
		}
		getLogicNode().setExpression(enf);
	}
	
	/**
	 * Sets Node functions to the ones provided.
	 * <br>
	 * Also sets Table type to NodeConfiguration.TableType.Partitioned.
	 * <br>
	 * If the node only has one non-simulated parent, this parent will be automatically used to partition this node.
	 * 
	 * @param functions functions to set
	 * 
	 * @throws NodeException if a function is invalid
	 */
	public void setTableFunctions(String[] functions) throws NodeException {
		throw new UnsupportedOperationException("Not implemented");
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
	public void partitionByParents(Node[] partitionParents) throws NodeException {
		throw new UnsupportedOperationException("Not implemented");
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
		if (isSimulated()){
			throw new NodeException("Can't set states for a simulated node");
		}
		
		ExtendedNode en = getLogicNode();
		
		DataSet ds = new DataSet();
		
		for(int s = 0; s < states.length; s++){
			
			String stateName = states[s].trim();
			
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
	 * Changes the Node into a simulated node subject to conditions.
	 * <br>
	 * Only ContinuousInterval and IntegerInterval nodes can be simulated.
	 * <br>
	 * This action replaces States with dynamic states.
	 * 
	 * @param simulated whether the node should be simulated or not
	 * 
	 * @throws NodeException if Node type can not be simulated
	 * 
	 * @return true if the Node has been changed or false if it already was simulated
	 */
	public boolean setSimulated(boolean simulated) throws NodeException {
		if (isSimulated()){
			return false;
		}
		
		if (getLogicNode() instanceof ContinuousEN && !(getLogicNode() instanceof RankedEN)){
			NodeConfiguration.setDefaultIntervalStates(this);
			ContinuousEN cien = (ContinuousEN)this.getLogicNode();
			cien.setSimulationNode(true);
			return true;
		}
		
		throw new NodeException("Simulation is non-applicable to node type of "+toStringExtra()+" can not be simulated");
	}
	
	/**
	 * Returns true of the Node is simulated and false otherwise.
	 * 
	 * @return true of the Node is simulated and false otherwise
	 */
	public boolean isSimulated(){
		if (getLogicNode() instanceof ContinuousEN && !(getLogicNode() instanceof RankedEN)){
			return ((ContinuousEN)getLogicNode()).isSimulationNode();
		}
		return false;
	}
	
	/**
	 * Returns toStringExtra().
	 * 
	 * @return toStringExtra()
	 */
	@Override
	public String toString(){
		return toStringExtra();
	}
	
	/**
	 * Returns `network`.`node` String representing this Node.
	 * 
	 * @return detailed String representing this Node
	 */
	public String toStringExtra(){
		return "`"+getNetwork().getId()+"`.`"+this.getId()+"`";
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
			getLogicNode().updateConnNodeId(newId);
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
	public JSONObject toJSONObject() {
//		if (json.has(Ref.META)){
//			node.meta = json.optJSONObject(Ref.META);
//		}
//		
//		if (json.has(Ref.GRAPHICS)){
//			node.graphics = json.optJSONObject(Ref.GRAPHICS);
//		}
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
}
