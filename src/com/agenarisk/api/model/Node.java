package com.agenarisk.api.model;

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
import java.util.HashSet;
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
import com.agenarisk.api.Ref;
import com.agenarisk.api.exception.NetworkException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeMethodNotSupportedException;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.NameDescription;
import uk.co.agena.minerva.util.model.Range;
import uk.co.agena.minerva.util.nptgenerator.ExpressionParser;

/**
 * Node class represents an equivalent to a Node in AgenaRisk Desktop or ExtendedBN in AgenaRisk Java API v1
 * @author Eugene Dementiev
 */
public class Node implements Networked<Node>, Comparable<Node>, Identifiable<NodeException>, Storable {
	
	/**
	 * The Network containing this Node
	 */
	private final Network network;
	
	/**
	 * Incoming Links from other Nodes
	 */
	private final Set<Link> linksIn = Collections.synchronizedSet(new HashSet<>());
	
	/**
	 * Outgoing Links to other Nodes
	 */
	private final Set<Link> linksOut = Collections.synchronizedSet(new HashSet<>());
	
	/**
	 * Corresponding ExtendedNode
	 */
	private final ExtendedNode logicNode;
	
	/**
	 * Constructor for the Node class. Creates a Node object without affecting the logic in any way
	 * @param network the Network that will contain this Node
	 * @param logicNode the Node's corresponding logic Node
	 */
	private Node(Network network, ExtendedNode logicNode){
		this.network = network;
		this.logicNode = logicNode;
	}
	
	/**
	 * Returns a copy of the list of incoming Links. The membership is only guaranteed to be valid at the time of request and is not maintained.
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
	 * @return a set of Node's parents
	 */
	@Override
	public synchronized Set<Node> getParents() {
		return getLinksIn().stream().map(link -> link.getFromNode()).collect(Collectors.toSet());
	}

	/**
	 * Builds and returns a set of Node's children, which is valid at the time of request. This set will not reflect any membership changes made afterwards.
	 * @return a set of Node's children
	 */
	@Override
	public synchronized Set<Node> getChildren() {
		return getLinksOut().stream().map(link -> link.getToNode()).collect(Collectors.toSet());
	}
	
	/**
	 * Removes the Link object from the linksOut list
	 * @param link the Link to remove
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
	 * Removes the Link object from the linksOut list
	 * @param link the Link to remove
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
	 * Creates a simple Link from this Node to given Node in the same Network
	 * If child table is in Manual mode, this action will reset it to a uniform table
	 * @param child the child Node
	 * @return created Link
	 * @throws LinkException if Link already exists, or a cross network link is being created (use Node.linkNodes() for that instead)
	 */
	public synchronized Link linkTo(Node child) throws LinkException {
		return Node.linkNodes(this, child);
	}
	
	/**
	 * Creates a simple Link from the given Node to this Node in the same Network
	 * If this Node's table is in Manual mode, this action will reset it to a uniform table
	 * @param parent the parent Node
	 * @return created Link
	 * @throws LinkException if Link already exists, or a cross network link is being created (use Node.linkNodes() for that instead)
	 */
	public synchronized Link linkFrom(Node parent) throws LinkException {
		return Node.linkNodes(parent, this);
	}
	
	/**
	 * Checks that there is a Link between two nodes and destroys it. No effect if there is no link between these two nodes
	 * @param node1 Node 1
	 * @param node2 Node 2
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
	 * Creates a simple Link between two nodes in the same Network
	 * @param fromNode Node to link from
	 * @param toNode Node to link to
	 * @return created Link
	 * @throws LinkException if Link already exists, or a cross network link is being created
	 */
	public static Link linkNodes(Node fromNode, Node toNode) throws LinkException {
		return linkNodes(fromNode, toNode, null, null);
	}
	
	/**
	 * Creates a Link between two nodes in same or different Networks
	 * @param fromNode Node to link from
	 * @param toNode Node to link to
	 * @param type type of CrossNetworkLink if applicable
	 * @return created Link
	 * @throws LinkException if Link already exists, or a cross network link is being created with invalid arguments
	 */
	public static Link linkNodes(Node fromNode, Node toNode, Ref.LINK_TYPE type) throws LinkException {
		return linkNodes(fromNode, toNode, type, null);
	}
	
	/**
	 * Creates a Link between two nodes in same or different Networks
	 * The underlying NPT of the child node will be reset to some default value
	 * @param fromNode Node to link from
	 * @param toNode Node to link to
	 * @param type type of CrossNetworkLink if applicable
	 * @param stateToPass if type is State, this specifies the state to pass
	 * @return created Link
	 * @throws LinkException if Link already exists, or a cross network link is being created with invalid arguments
	 */
	public static Link linkNodes(Node fromNode, Node toNode, Ref.LINK_TYPE type, String stateToPass) throws LinkException {
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
			
			if(true)throw new UnsupportedOperationException("If the link was created, for same-network links we need to reapply child's NPT");
			
			return link;
		}
	}
	
	/**
	 * Factory method to create a Node for use by the Network class.
	 * Creates the underlying logic objects
	 * @param network Network that the Node will be added to
	 * @param json configuration of the Node
	 * @return created Node
	 * @throws NodeException if JSON configuration is incomplete or invalid; or if there was an error in the logic
	 */
	protected static Node createNode(Network network, JSONObject json) throws NodeException {
		String id;
		String name;
		JSONObject jsonDefinition; 
		Ref.NODE_TYPE type;
		
		try {
			id = json.getString(Ref.ID);
			name = json.getString(Ref.NAME);
			jsonDefinition = json.getJSONObject(Ref.CONFIGURATION);
			String typeString = jsonDefinition.getString(Ref.TYPE);
			type = Ref.NODE_TYPE.valueOf(typeString);
		}
		catch (JSONException ex){
			throw new NodeException(JSONUtils.createMissingAttrMessage(ex), ex);
		}
		
		String nodeClassName = resolveNodeClassName(type);
		
		ExtendedNode en;
		try {
			en = network.getLogicNetwork().createNewExtendedNode(nodeClassName, new NameDescription(name, name));
			network.getLogicNetwork().updateConnNodeId(en, id);
		}
		catch (CoreBNNodeNotFoundException | ExtendedBNException ex){
			throw new NodeException("Failed to create a node for ID `"+id+"`", ex);
		}
		
		Node node = new Node(network, en);
		
		boolean simulated = false;
		if (jsonDefinition.has(Ref.SIMULATED)){
			try {
				simulated = jsonDefinition.getBoolean(Ref.SIMULATED);
			}
			catch (JSONException ex){
				throw new NodeException("Invalid simulation attribute value", ex);
			}
		}
		
		if (simulated){
			ContinuousEN cien = (ContinuousEN)en;
			setDefaultIntervalStates(node);
			cien.setSimulationNode(true);

		}
		else {
			try {
				node.setStates(jsonDefinition.getJSONArray(Ref.STATES));
			}
			catch (JSONException ex){
				// Should not happen
				throw new AgenaRiskRuntimeException("Failed to access states array", ex);
			}
		}
		
		return node;
	}
	
	/**
	 * Sets the manual NPT according to columns provided
	 * @param columns 2D array where first dimension are the columns and second dimension are the cells
	 * @throws NodeException if provided table size is wrong or Node does not allow manual NPT
	 */
	public void setTableColumns(double[][] columns) throws NodeException {
		// Set manual table
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Sets the manual NPT according to rows provided
	 * @param rows 2D array where first dimension are the rows and second dimension are the cells
	 * @throws NodeException if provided table size is wrong or Node does not allow manual NPT
	 */
	public void setTableRows(double[][] rows) throws NodeException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Replaces the Node's probability table with one specified in the given JSON
	 * @param jsonTable configuration of the table in JSON format
	 * @throws NodeException if table type does not match the rest of the table configuration, if table is not a square matrix or the number of cells does not match the number of partitions created by the combination of parent states
	 */
	public void setTable(JSONObject jsonTable) throws NodeException {
		if (jsonTable.length() == 0){
			return;
		}
		
		List<String> parentIDs = getParents().stream().map(node -> node.getId()).collect(Collectors.toList());
		try {
			String tableType = jsonTable.getString(Ref.TYPE);

			if (tableType.equalsIgnoreCase(Ref.TABLE_TYPE.Manual.toString())){
				
				if (this.isSimulated()){
					throw new NodeException("Can't set a manual NPT for a simulated node");
				}
				
				try {
					double[][] npt = extractNPTColumns(jsonTable.getJSONArray(Ref.PROBABILITIES));
					List<ExtendedNode> parentNodes = getParents().stream().map(node -> node.getLogicNode()).collect(Collectors.toList());
					getLogicNode().setNPT(npt, parentNodes);
				}
				catch (ExtendedBNException ex){
					throw new NodeException("Failed to extract NPT", ex);
				}
				catch (ArrayIndexOutOfBoundsException ex){
					throw new NodeException("NPT may not be a square matrix", ex);
				}
			}
			else if (tableType.equalsIgnoreCase(Ref.TABLE_TYPE.Expression.toString())){
				String expression = jsonTable.getJSONArray(Ref.EXPRESSIONS).getString(0);
				ExtendedNodeFunction enf;
				try {
					enf = ExpressionParser.parseFunctionFromString(expression, parentIDs);
				}
				catch (ParseException ex){
					throw new JSONException("Unable to parse node function `"+expression+"`", ex);
				}
				getLogicNode().setExpression(enf);
			}
			else if (tableType.equalsIgnoreCase(Ref.TABLE_TYPE.Partitioned.toString())){
				// Get parents used for partitioning (can be only a subset of all parents)
				List<String> partitionParentIDs = JSONUtils.toList(jsonTable.getJSONArray(Ref.PARTITIONS), String.class);
				List<ExtendedNode> partitionParentNodes = new ArrayList<>();
				partitionParentIDs.stream().forEach(parentID -> {
					ExtendedNode parent = getNetwork().getLogicNetwork().getExtendedNodeWithUniqueIdentifier(parentID);
					if (parent == null){
						throw new IllegalArgumentException("No such parent `"+parentID+"` found");
					}
					partitionParentNodes.add(parent);
				});
				getLogicNode().setPartitionedExpressionModelNodes(partitionParentNodes);

				// Create functions
				List<ExtendedNodeFunction> enfs = new ArrayList<>();
				List<String> expressions = JSONUtils.toList(jsonTable.getJSONArray(Ref.EXPRESSIONS), String.class);

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
	 * Resets Node table partitioning and sets Table type to Ref.TABLE_TYPE.Expression.
	 * @param function function to set
	 * @throws NodeException if function is invalid
	 */
	public void setTableFunction(String function) throws NodeException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Sets Node functions to the ones provided.
	 * Also sets Table type to Ref.TABLE_TYPE.Partitioned.
	 * If the node only has one non-simulated parent, this parent will be automatically used to partition this node.
	 * @param functions functions to set
	 * @throws NodeException if a function is invalid
	 */
	public void setTableFunctions(String[] functions) throws NodeException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Sets node partitions based on the parent states.
	 * Order of partitions will be based on the order of nodes in the array and states within them.
	 * @param partitionParents parents to partition by
	 * @throws NodeException if one of the nodes in partitionParents is simulated or is not a parent of this Node
	 */
	public void partitionByParents(Node[] partitionParents) throws NodeException {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * NPT in JSON is given by rows, while ExtendedNode expects an array of columns, so we will need to invert it.
	 * @param jsonNPT
	 * @return 2D array where first dimension are the columns and second dimension are the cells
	 * @throws JSONException 
	 */
	private static double[][] extractNPTColumns(JSONArray jsonNPT) throws JSONException {
		int rows = jsonNPT.length();
		int cols = jsonNPT.getJSONArray(0).length();
		double[][] npt = new double[cols][rows];
		
		for(int r = 0; r < jsonNPT.length(); r++){
			JSONArray jsonCells = jsonNPT.getJSONArray(r);
			for(int c = 0; c < jsonCells.length(); c++){
				double cell = jsonCells.getDouble(c);
				npt[c][r] = cell;
			}
				
		}
		
		//System.out.println("NPT created:");
		//System.out.println(org.apache.commons.lang3.ArrayUtils.toString(npt));

		return npt;
	}
	
	/**
	 * Replaces Node's states by the ones given in the array.
	 * This action resets the probability table to uniform.
	 * @param states new Node's states
	 * @throws NodeException if state is an invalid range; or if the Node is simulated
	 */
	public void setStates(String[] states) throws NodeException{
		throw new UnsupportedOperationException("Not implemented");
	}
	
	/**
	 * Replaces Node's states by the ones given in the JSON array.
	 * This action resets the probability table to uniform.
	 * @param states new Node's states
	 * @throws NodeException if state is an invalid range; or if the Node is simulated
	 */
	public void setStates(JSONArray states) throws NodeException{
		
		if (isSimulated()){
			throw new NodeException("Can't set states for a simulated node");
		}
		
		ExtendedNode en = getLogicNode();
		
		DataSet ds = new DataSet();
		
		for(int s = 0; s < states.length(); s++){
			
			String stateName;
			try {
				stateName = states.getString(s).trim();
			}
			catch (JSONException ex){
				// Should not happen
				throw new AgenaRiskRuntimeException("Failed to parse states array", ex);
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
	 * Changes the Node into a simulated node subject to conditions.
	 * Only ContinuousInterval and IntegerInterval nodes can be simulated.
	 * This action replaces States with dynamic states.
	 * @param simulated whether the node should be simulated or not
	 * @throws NodeException if Node type can not be simulated
	 * @return true if the Node has been changed or false if it already was simulated
	 */
	public boolean setSimulated(boolean simulated) throws NodeException {
		if (isSimulated()){
			return false;
		}
		
		if (getLogicNode() instanceof ContinuousEN && !(getLogicNode() instanceof RankedEN)){
			setDefaultIntervalStates(this);
			ContinuousEN cien = (ContinuousEN)this.getLogicNode();
			cien.setSimulationNode(true);
			return true;
		}
		
		throw new NodeException("Simulation is non-applicable to node type of "+toStringExtra()+" can not be simulated");
	}
	
	/**
	 * Returns true of the Node is simulated and false otherwise
	 * @return true of the Node is simulated and false otherwise
	 */
	public boolean isSimulated(){
		if (getLogicNode() instanceof ContinuousEN && !(getLogicNode() instanceof RankedEN)){
			return ((ContinuousEN)getLogicNode()).isSimulationNode();
		}
		return false;
	}
	
	/**
	 * Returns toStringExtra()
	 * @return toStringExtra()
	 */
	@Override
	public String toString(){
		return toStringExtra();
	}
	
	/**
	 * Returns `network`.`node` String representing this Node
	 * @return detailed String representing this Node
	 */
	public String toStringExtra(){
		return "`"+getNetwork().getId()+"`.`"+this.getId()+"`";
	}

	/**
	 * Returns the Network containing this Node
	 * @return the Network containing this Node
	 */
	public Network getNetwork() {
		return network;
	}

	/**
	 * Returns the underlying ExtendedNode
	 * @return the underlying ExtendedNode
	 */
	protected ExtendedNode getLogicNode() {
		return logicNode;
	}

	/**
	 * Gets the ID of this Node
	 * @return the ID of this Node
	 */
	@Override
	public String getId() {
		return getLogicNode().getConnNodeId();
	}

	/**
	 * Changes the ID of this Node to the provided ID, if the new ID is not already taken
	 * Will lock IDContainer.class while doing so
	 * @param id the new ID
	 * @throws NodeException if fails to change ID
	 */
	@Override
	public void setId(String id) throws NodeException {
		try {
			getNetwork().changeContainedId(this, id);
		}
		catch (NetworkException ex){
			throw new NodeException("Failed to change ID of Network `" + getId() + "`", ex);
		}
		
		getLogicNode().setConnNodeId(id);
	}
	
	/**
	 * Sets the name of this Node
	 * @param name new name
	 */
	public void setName(String name){
		getLogicNode().getName().setShortDescription(name);
	}
	
	/**
	 * Gets the name of this node
	 * @return the name of this node
	 */
	public String getName(){
		return getLogicNode().getName().getShortDescription();
	}
	
	/**
	 * Sets the description of this Node
	 * @param description new description
	 */
	public void setDescription(String description){
		getLogicNode().getName().setLongDescription(description);
	}
	
	/**
	 * Gets the description of this node
	 * @return the description of this node
	 */
	public String getDescription(){
		return getLogicNode().getName().getLongDescription();
	}

	/**
	 * Compares this Node object to another based on its underlying logic network and node IDs
	 * @param o another Node object
	 * @return String comparison of toStringExtra() of both Nodes
	 */
	@Override
	public int compareTo(Node o) {
		return this.toStringExtra().compareTo(o.toStringExtra());
	}
	
	/**
	 * Checks equality of a given object to this Node. Returns true if logic nodes of both objects are the same
	 * @param obj The object to compare this Node against
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
	 * @return a hash code value for this object.
	 */
	@Override
	public int hashCode() {
		return System.identityHashCode(getLogicNode());
	}
	
	/**
	 * Removes a link between this Node and the linkedNode, if such link exists.
	 * Will check incoming and outgoing Links
	 * Both Nodes will no longer refer to each other
	 * @param linkedNode the Node to break Link with
	 * @return false if there was no Link; or true if the Link was removed
	 */
	@Override
	public boolean unlink(Node linkedNode){
		return Node.unlinkNodes(this, linkedNode);
	}
	
	/**
	 * Gets fully-qualified name for ExtendedNode concrete implementation matching the provided Node type from Ref.NODE_TYPE
	 * @param type Node type
	 * @return fully-qualified ExtendedNode subclass name
	 * @throws NodeException if no matching ExtendedNode implementation found
	 */
	protected static String resolveNodeClassName(Ref.NODE_TYPE type) throws NodeException{
		String nodeClassName;
		switch (type) {
			case Boolean:
				nodeClassName = BooleanEN.class.getName();
				break;
			case Labelled:
				nodeClassName = LabelledEN.class.getName();
				break;
			case Ranked:
				nodeClassName = RankedEN.class.getName();
				break;
			case DiscreteReal:
				nodeClassName = DiscreteRealEN.class.getName();
				break;
			case ContinuousInterval:
				nodeClassName = ContinuousIntervalEN.class.getName();
				break;
			case IntegerInterval:
				nodeClassName = IntegerIntervalEN.class.getName();
				break;
			default:
				throw new NodeException("Invalid node type provided");
		}
		
		return nodeClassName;
	}

	/**
	 * Creates a JSON representing this Node, ready for file storage
	 * @return JSONObject representing this Node
	 */
	@Override
	public JSONObject toJSONObject() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	
	/**
	 * Replaces Node states with 3 default interval states
	 * @param node 
	 */
	private static void setDefaultIntervalStates(Node node){
		ContinuousEN cien = (ContinuousEN)node.getLogicNode();
		DataSet cids = new DataSet();                          
		cids.addIntervalDataPoint(Double.NEGATIVE_INFINITY, -1);
		cids.addIntervalDataPoint(-1, 1);
		cids.addIntervalDataPoint(1, Double.POSITIVE_INFINITY);

		try {
			cien.removeExtendedStates(0, cien.getExtendedStates().size()-1, true);
			cien.createExtendedStates(cids);
		}
		catch (ExtendedBNException | MinervaRangeException ex){
			throw new AgenaRiskRuntimeException("Failed to initialise interval states for node " + node.toStringExtra(), ex);
		}
	}
}
