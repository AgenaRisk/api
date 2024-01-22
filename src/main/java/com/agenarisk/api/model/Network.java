package com.agenarisk.api.model;

import com.agenarisk.api.model.interfaces.Named;
import com.agenarisk.api.model.interfaces.Networked;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Picture;
import com.agenarisk.api.io.stub.RiskTable;
import com.agenarisk.api.io.stub.Text;
import com.agenarisk.api.model.field.Id;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import com.agenarisk.api.model.interfaces.IdContainer;
import com.agenarisk.api.util.Advisory;
import java.util.Optional;

/**
 * Network class represents an equivalent to a Risk Object in AgenaRisk Desktop or ExtendedBN in AgenaRisk Java API v1.
 * 
 * @author Eugene Dementiev
 */
public class Network implements Networked<Network>, Comparable<Network>, Identifiable<NetworkException>, IdContainer<NetworkException>, Storable, Named {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		networks,
		network,
		id,
		name,
		description
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum ModificationLog {
		modificationLog,
		entry,
		action,
		description
	}
	
	/**
	 * Model that contains this Network
	 */
	private final Model model;
	
	/**
	 * Corresponding ExtendedBN
	 */
	private ExtendedBN logicNetwork;
	
	/**
	 * Should be set on model load, and then saved on model save
	 */
	private JSONObject jsonGraphics, riskTable, texts, pictures;
	
	
	/**
	 * ID-Node map of this Network
	 * <br>
	 * This should not be directly returned to other components and should be modified only by this class in a block synchronized on IDContainer.class
	 */
	private final Map<Id, Node> nodes = Collections.synchronizedMap(new LinkedHashMap<>());
	
	/**
	 * Factory method to be called by a Model object that is trying to add a Network to itself.
	 * 
	 * @param model the Model to add a Network to
	 * @param id the ID of the Network
	 * @param name the name of the Network
	 * 
	 * @return the created Network
	 */
	protected static Network createNetwork(Model model, String id, String name) {
		// Call private constructor
		return new Network(model, id, name);
	}
	
	/**
	 * Factory method to be called by a Model object that is trying to add a Network to itself.
	 * <br>
	 * Note: loading node tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param model the Model to add a Network to
	 * @param jsonNetwork JSONObject representing the network, including structure, tables, graphics etc
	 * 
	 * @return the created Network
	 * 
	 * @see Node#setTable(JSONObject)
	 * 
	 * @throws JSONException if JSON configuration is incomplete or invalid
	 * @throws NetworkException if failed to load a network or node or if an object not found
	 */
	protected static Network createNetwork(Model model, JSONObject jsonNetwork) throws JSONException, NetworkException {
		return createNetwork(model, jsonNetwork, true);
	}
	
	/**
	 * Factory method to be called by a Model object that is trying to add a Network to itself.
	 * <br>
	 * Note: loading node tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param model the Model to add a Network to
	 * @param jsonNetwork JSONObject representing the network, including structure, tables, graphics etc
	 * @param withTables whether to load node tables from JSON
	 * 
	 * @return the created Network
	 * 
	 * @see Node#setTable(JSONObject)
	 * 
	 * @throws JSONException if JSON configuration is incomplete or invalid
	 * @throws NetworkException if failed to load a network or node or if an object not found
	 */
	protected static Network createNetwork(Model model, JSONObject jsonNetwork, boolean withTables) throws JSONException, NetworkException {
		String id = jsonNetwork.getString(Network.Field.id.toString());
		String name = jsonNetwork.optString(Network.Field.name.toString());
		String description = jsonNetwork.optString(Network.Field.description.toString());
		
		if (name.isEmpty()){
			name = id;
		}
		
		Network network;
		try {
			// Don't know if can add with this ID, ask model
			network = model.createNetwork(id, name);
			network.setDescription(description);
		}
		catch (NetworkException ex){
			throw new NetworkException("Failed to add a network to model with ID `" + id + "`", ex);
		}
		
		// Create nodes
		JSONArray jsonNodes = jsonNetwork.getJSONArray(Node.Field.nodes.toString());
		if (jsonNodes != null){
			for(int i = 0; i < jsonNodes.length(); i++){
				network.createNode(jsonNodes.getJSONObject(i), false);
			}
		}
		
		// Create links
		JSONArray jsonLinks = jsonNetwork.optJSONArray(Link.Field.links.toString());
		if (jsonLinks != null){
			for(int i = 0; i < jsonLinks.length(); i++){
				JSONObject jsonLink = jsonLinks.getJSONObject(i);
				String parentId = jsonLink.optString(Link.Field.parent.toString());
				String childId = jsonLink.optString(Link.Field.child.toString());

				Node parent = network.getNode(parentId);
				Node child = network.getNode(childId);

				if (parent == null){
					throw new NetworkException("Node `" + network.getId() + "`.`" + parentId + "` not found");
				}

				if (child == null){
					throw new NetworkException("Node `" + network.getId() + "`.`" + childId + "` not found");
				}

				try {
					parent.linkTo(child);
				}
				catch (LinkException ex){
					if (Advisory.getCurrentThreadGroup() != null){
						String message = "Ignored link from " + parent.toStringExtra() + " to " + child.toStringExtra() + ": " + ex.getMessage();
						Advisory.getCurrentThreadGroup().addMessage(new Advisory.AdvisoryMessage(message));
					}
					else {
						throw new NetworkException("Failed to link nodes " + parent.toStringExtra() + " and " + child.toStringExtra(), ex);
					}
				}
			}
		}
		
		// Load node tables
		if (withTables && jsonNodes != null){
			for(int i = 0; i < jsonNodes.length(); i++){
				JSONObject jsonNode = jsonNodes.getJSONObject(i);
				Node node = network.getNode(jsonNode.getString(Node.Field.id.toString()));

				JSONObject jsonConfiguration = jsonNode.getJSONObject(NodeConfiguration.Field.configuration.toString());
				JSONObject jsonTable = jsonConfiguration.optJSONObject(NodeConfiguration.Table.table.toString());
				try {
					node.setTable(jsonTable);
				}
				catch (NodeException ex){
					throw new NetworkException("Failed to load table for node " + node.toStringExtra(), ex);
				}
			}
		}
		
		// Load stored JSON objects
		
		if (jsonNetwork.has(Graphics.Field.graphics.toString())){
			network.jsonGraphics = Optional.ofNullable(jsonNetwork.optJSONObject(Graphics.Field.graphics.toString())).orElse(new JSONObject());
		}
		
		if (jsonNetwork.has(RiskTable.Field.riskTable.toString())){
			network.riskTable = jsonNetwork.optJSONObject(RiskTable.Field.riskTable.toString());
		}
		
		if (jsonNetwork.has(Text.Field.texts.toString())){
			network.texts = jsonNetwork.optJSONObject(Text.Field.texts.toString());
		}
		
		if (jsonNetwork.has(Picture.Field.pictures.toString())){
			network.pictures = jsonNetwork.optJSONObject(Picture.Field.pictures.toString());
		}
		
		return network;
	}
	
	/**
	 * Constructor for Network class, to be used by createNetwork method.
	 * <br>
	 * Creates the logic network and sets its name and id
	 * 
	 * @param model the Model that this Network belongs to
	 * @param id the ID of the Network
	 * @param name the name of the Network
	 */
	private Network(Model model, String id, String name) {
		this.model = model;
		
		try {
			logicNetwork = model.getLogicModel().addExtendedBN(name, "");
			logicNetwork.setConnID(id);

		}
		catch (ExtendedBNException ex){
			// Should not really happen
			throw new AgenaRiskRuntimeException("Failed to create a new network", ex);
		}
		
	}
	
	/**
	 * Creates a Node and adds it to this Network.
	 * 
	 * @param id ID of the Node
	 * @param name name of the Node
	 * @param type type of the Node
	 * 
	 * @return the created Node
	 * 
	 * @throws NetworkException if Node creation failed
	 */
	public Node createNode(String id, String name, Node.Type type) throws NetworkException {
		synchronized (IdContainer.class){
			if (nodes.containsKey(new Id(id))){
				throw new NetworkException("Node with id `" + id + "` already exists");
			}
			nodes.put(new Id(id), null);
		}
		
		Node node;
		
		try {
			node = Node.createNode(this, id, name, type);
			nodes.put(new Id(id), node);
		}
		catch (AgenaRiskRuntimeException ex){
			nodes.remove(new Id(id));
			throw new NetworkException("Failed to add node `" + id + "`", ex);
		}
		
		return node;
	}
	
	/**
	 * Creates a Node and adds it to this Network.
	 * 
	 * @param id ID of the Node
	 * @param type type of the Node
	 * 
	 * @return the created Node
	 * 
	 * @throws NetworkException if Node creation failed
	 */
	public Node createNode(String id, Node.Type type) throws NetworkException {
		return createNode(id, id, type);
	}
	
	/**
	 * Creates a Node from its JSONObject specification and adds it to this Network.
	 * <br>
	 * Note: loading node tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param jsonNode JSONObject with full Node's configuration
	 * @param withTables whether to load node tables from JSON
	 * 
	 * @return the created Node
	 * 
	 * @see Node#setTable(JSONObject)
	 * 
	 * @throws NetworkException if Node creation failed (Node with given ID already exists; or JSON configuration is missing required attributes)
	 */
	public Node createNode(JSONObject jsonNode, boolean withTables) throws NetworkException {
		
		Node node;
		try {
			node = Node.createNode(this, jsonNode, withTables);
		}
		catch (NodeException | JSONException ex){
			throw new NetworkException("Failed to create Node", ex);
		}
		
		return node;
	}
	
	/**
	 * Creates a Node from its JSONObject specification and adds it to this Network.
	 * <br>
	 * Note: loading node tables will fail if parent nodes do not exist in the model. In that case load all nodes first without tables and then use <code>setTable(JSONObject)</code> after all nodes, states, intra and cross network links had been created.
	 * 
	 * @param jsonNode JSONObject with full Node's configuration
	 * 
	 * @return the created Node
	 * 
	 * @see Node#setTable(JSONObject)
	 * 
	 * @throws NetworkException if Node creation failed (Node with given ID already exists; or JSON configuration is missing required attributes)
	 */
	public Node createNode(JSONObject jsonNode) throws NetworkException {
		return createNode(jsonNode, true);
	}

	/**
	 * Gets the ID of this Network.
	 * 
	 * @return the ID of this Network
	 */
	@Override
	public final String getId() {
		return getLogicNetwork().getConnID();
	}
	
	/**
	 * Changes the ID of this Network to the provided ID, if the new ID is not already taken.
	 * <br>
	 * Will lock IDContainer.class while doing so.
	 * 
	 * @param id the new ID
	 * 
	 * @throws NetworkException if fails to change ID
	 */
	@Override
	public final void setId(String id) throws NetworkException {
		
		try {
			getModel().changeContainedId(this, id);
		}
		catch (ModelException ex){
			throw new NetworkException("Failed to change ID of Network `" + getId() + "`", ex);
		}
		
		getLogicNetwork().setConnID(id);
	}

	/**
	 * Returns the underlying logical ExtendedBN network.
	 * <br>
	 * Using logic objects directly is <b>unsafe</b> and is likely to break something.
	 * 
	 * @return the underlying logical ExtendedBN network
	 * 
	 * @deprecated Will be made module-restricted in the future.
	 */
	public final ExtendedBN getLogicNetwork() {
		return logicNetwork;
	}

	/**
	 * Returns the Model that this Network belongs to.
	 * 
	 * @return the Model that this Network belongs to
	 */
	public final Model getModel() {
		return model;
	}
	
	/**
	 * Compares this Network object to another based on the Id of this object.
	 * 
	 * @param o another Network object
	 * 
	 * @return a negative integer, zero, or a positive integer if the value of this object's ID precedes the one of the specified object's ID
	 * @see Id#compareTo(Id) 
	 */
	@Override
	public synchronized int compareTo(Network o) {
		// Sync to prevent wrong comparisons because ID was changed by another thread
		return new Id(getId()).compareTo(new Id(o.getId()));
	}
	
	/**
	 * Checks equality of a given object to this Network. Returns true if logic networks of both objects are the same.
	 * 
	 * @param obj The object to compare this Network against
	 * 
	 * @return true if the given object represents the same Network as this Network, false otherwise
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Network)){
			return false;
		}
		
		return this.getLogicNetwork() == ((Network)obj).getLogicNetwork();
	}

	/**
	 * Returns a hash code value for this object.
	 * 
	 * @return a hash code value for this object.
	 */
	@Override
	public int hashCode() {
		return System.identityHashCode(getLogicNetwork());
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
	 * Returns the ID of the underlying network surrounded by back ticks.
	 * 
	 * @return the ID of the underlying network surrounded by back ticks
	 */
	public String toStringExtra(){
		return "`" + getId() + "`";
	}

	/**
	 * Builds and returns a set of Networks, which are parents of this Network.
	 * <br>
	 * Networks are connected with Links between their Nodes.
	 * <br>
	 * So for two Networks Net1 and Net2 and Nodes Node1 and Node2, where Node1 belongs to Net1 and Node2 belongs to Net2, and Node1 → Node2, Net2 is the child of Net1.
	 * 
	 * @return a set of Networks that are parents of this Network
	 */
	@Override
	public Set<Network> getParents() {
		Set<Network> nets = new LinkedHashSet<>();
		nodes.values().forEach(node -> {
			node.getLinksIn().stream().map((link) -> link.getFromNode().getNetwork()).filter((net) -> (!Objects.equals(net, this))).forEachOrdered((net) -> {
				nets.add(net);
			});
		});
		return nets;
	}

	/**
	 * Builds and returns a set of Networks, which are children of this Network.
	 * <br>
	 * Networks are connected with Links between their Nodes.
	 * <br>
	 * So for two Networks Net1 and Net2 and Nodes Node1 and Node2, where Node1 belongs to Net1 and Node2 belongs to Net2, and Node1 → Node2, Net2 is the child of Net1.
	 * 
	 * @return a set of Networks that are children of this Network
	 */
	@Override
	public Set<Network> getChildren() {
		Set<Network> nets = new LinkedHashSet<>();
		nodes.values().forEach(node -> {
			node.getLinksOut().stream().map((link) -> link.getToNode().getNetwork()).filter((net) -> (!Objects.equals(net, this))).forEachOrdered((net) -> {
				nets.add(net);
			});
		});
		return nets;
	}

	/**
	 * Returns a copy of the incoming Links list.
	 * 
	 * @return a copy of the incoming Links list
	 */
	@Override
	public List<Link> getLinksIn() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	/**
	 * Returns a copy of the outgoing Links list.
	 * 
	 * @return a copy of the outgoing Links list
	 */
	@Override
	public List<Link> getLinksOut() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	/**
	 * Removes all links (if any exist) between the two networks.
	 * 
	 * @param network the Network to sever connections with
	 * 
	 * @return false if Network objects are the same, true otherwise
	 */
	@Override
	public boolean unlink(Network network) {
		if (Objects.equals(this, network)){
			return false;
		}
		
		getModel().getLogicModel().removeAllMessageParsesBetweenBNs(this.getLogicNetwork(), network.getLogicNetwork());
		getModel().getLogicModel().removeAllMessageParsesBetweenBNs(network.getLogicNetwork(), this.getLogicNetwork());
		
		// Go through all network nodes
		nodes.values().forEach(node -> {
			
			// Get all node's links
			Stream.of(node.getLinksOut().stream(), node.getLinksIn().stream()).flatMap(java.util.function.Function.identity()).forEachOrdered(link -> {
				if (!(link instanceof CrossNetworkLink)){
					return;
				}
				
				// Is the link between this and given Networks?
				boolean incoming = Objects.equals(((Link)link).getFromNode().getNetwork(),network) && Objects.equals(((Link)link).getToNode().getNetwork(),this);
				boolean outgoing = Objects.equals(((Link)link).getFromNode().getNetwork(),this) && Objects.equals(((Link)link).getToNode().getNetwork(),network);
				boolean removeLink = incoming || outgoing;
				
				if (!removeLink){
					return;
				}
				
				Node.unlinkNodes(link.getFromNode(), link.getToNode());
				
			});
			
		});
		
		return true;
	}
	
	/**
	 * @throws NetworkException when invalid type requested
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public Map<Id,? extends Identifiable> getIdMap(Class<? extends Identifiable> idClassType) throws NetworkException {
		if (Node.class.equals(idClassType)){
			return nodes;
		}
		throw new NetworkException("Invalid class type provided: "+idClassType);
	}

	/**
	 * @throws NetworkException when invoked
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwIdExistsException(String id) throws NetworkException {
		throw new NetworkException("Node with id `" + id + "` already exists");
	}
	
	/**
	 * @throws NetworkException when invoked
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwOldIdNullException(String id) throws NetworkException {
		throw new NetworkException("Can't change Node ID to `" + id + "` because the Node does not exist in this Network or old ID is null");
	}
	
	/**
	 * Gets Node from the Network by its unique ID.
	 * 
	 * @param id the ID of the Node
	 * 
	 * @return the Node with the given ID or null if no such node exists in the Network
	 */
	public Node getNode(String id){
		return nodes.get(new Id(id));
	}

	/**
	 * Returns a copy of ID-Node map.
	 * <br>
	 * Once generated, membership of this map is not maintained.
	 * 
	 * @return copy of ID-Node map
	 */
	public Map<String, Node> getNodes() {
		return nodes.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getValue(), e -> e.getValue(), (i, j) -> i, LinkedHashMap::new));
	}
	
	/**
	 * Returns Nodes of this Network as a list
	 * 
	 * @return list of Nodes
	 */
	public List<Node> getNodeList(){
		return new ArrayList<>(nodes.values());
	}

	/**
	 * Creates a JSON representing this Network, ready for file storage.
	 * 
	 * @return JSONObject representing this Network
	 */
	@Override
	public JSONObject toJson() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	public JSONObject getGraphicsJson(){
		return jsonGraphics;
	}

	/**
	 * Sets the name of this Network.
	 * 
	 * @param name new name
	 */
	@Override
	public void setName(String name){
		getLogicNetwork().getName().setShortDescription(name);
	}
	
	/**
	 * Gets the name of this Network.
	 * 
	 * @return the name of this Network
	 */
	@Override
	public String getName(){
		return getLogicNetwork().getName().getShortDescription();
	}
	
	/**
	 * Sets the description of this Network.
	 * 
	 * @param description new description
	 */
	@Override
	public void setDescription(String description){
		getLogicNetwork().getName().setLongDescription(description);
	}
	
	/**
	 * Gets the description of this Network.
	 * 
	 * @return the description of this Network
	 */
	@Override
	public String getDescription(){
		return getLogicNetwork().getName().getLongDescription();
	}

	/**
	 * Links this Network to an underlying Minerva Network object. Should only be used while wrapping a new Model around the Minerva Model.
	 * 
	 * @param logicNetwork the logical network
	 */
	protected void setLogicNetwork(ExtendedBN logicNetwork) {
		if (!new Id(getId()).equals(new Id(logicNetwork.getConnID()))){
			throw new AgenaRiskRuntimeException("Logic network id mismatch: " + getId() + "," + logicNetwork.getConnID());
		}
		
		this.logicNetwork = logicNetwork;
	}
	
	/**
	 * Removes the provided node from the model, severing its links to other Nodes and then removing it from its Network.
	 * 
	 * @param node Node to remove
	 * 
	 * @throws NodeException if the operation fails
	 */
	public void removeNode(Node node) throws NodeException {
		try {
			node.getParents().forEach(np -> Node.unlinkNodes(node, np));
			node.getChildren().forEach(nc -> Node.unlinkNodes(node, nc));
			nodes.remove(new Id(node.getId()));
			getLogicNetwork().removeExtendedNode(node.getLogicNode());
		}
		catch (ExtendedBNException ex){
			throw new NodeException("Failed to remove node " + node.toStringExtra(), ex);
		}
	}
	
	/**
	 * Regenerates Node NPTs in the Network, in descending order of the number of ancestors a Node has, starting from the root(s) of the Network.<br>
	 * Skips simulated and input nodes.
	 * 
	 * @throws NetworkException if regeneration fails in the logic
	 */
	protected void regenerateNPTs() throws NetworkException {
		
		nodes.values().stream().filter(n -> !n.isSimulated() && !n.isConnectedInput()).sorted((n1, n2) -> {
			return n1.getAncestors().size() - n2.getAncestors().size();
		}).forEach(n -> {
			try {
				getLogicNetwork().regenerateNPT(n.getLogicNode());
			}
			catch (Exception ex){
				throw new NetworkException("Failed to regenerate NPT for Node " + n.toStringExtra(), ex);
			}
		});
	}
	
}
