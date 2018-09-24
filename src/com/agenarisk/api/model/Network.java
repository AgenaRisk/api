package com.agenarisk.api.model;

import com.agenarisk.api.model.interfaces.Networked;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.model.interfaces.IDContainer;
import com.agenarisk.api.model.interfaces.Identifiable;
import com.agenarisk.api.model.interfaces.Storable;
import com.agenarisk.api.util.JSONUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import com.agenarisk.api.Ref;

/**
 * Network class represents an equivalent to a Risk Object in AgenaRisk Desktop or ExtendedBN in AgenaRisk Java API v1
 * @author Eugene Dementiev
 */
public class Network implements Networked<Network>, Comparable<Network>, Identifiable<NetworkException>, IDContainer<NetworkException>, Storable {
	
	/**
	 * Model that contains this Network
	 */
	private final Model model;
	
	/**
	 * Corresponding ExtendedBN
	 */
	private final ExtendedBN logicNetwork;
	
	/**
	 * Should be set on model load, and then saved on model save
	 */
	private JSONObject graphics, riskTable, texts, pictures;
	
	
	/**
	 * ID-Node map of this Network
	 * This should not be directly returned to other components and should be modified only by this class in a block synchronized on IDContainer.class
	 */
	private final Map<String, Node> nodes = Collections.synchronizedMap(new HashMap<>());
	
	/**
	 * Factory method to be called by a Model object that is trying to add a Network to itself
	 * @param model the Model to add a Network to
	 * @param id the ID of the Network
	 * @param name the name of the Network
	 * @return the created Network
	 */
	protected static Network createNetwork(Model model, String id, String name) {
		return new Network(model, id, name);
	}
	
	/**
	 * Factory method to be called by a Model object that is trying to add a Network to itself
	 * @param model the Model to add a Network to
	 * @param json JSONObject representing the network, including structure, tables, graphics etc
	 * @return the created Network
	 */
	protected static Network createNetwork(Model model, JSONObject json) {
		String id = "";
		String name = "";
		Network network = new Network(model, id, name);
		
		if (json.has(Ref.GRAPHICS)){
			network.graphics = json.optJSONObject(Ref.GRAPHICS);
		}
		
		if (json.has(Ref.RISK_TABLE)){
			network.riskTable = json.optJSONObject(Ref.RISK_TABLE);
		}
		
		if (json.has(Ref.TEXTS)){
			network.texts = json.optJSONObject(Ref.TEXTS);
		}
		
		if (json.has(Ref.PICTURES)){
			network.pictures = json.optJSONObject(Ref.PICTURES);
		}
		
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	/**
	 * Constructor for Network class, to be used by createNetwork method
	 * @param model the Model that this Network belongs to
	 * @param id the ID of the Network
	 * @param name the name of the Network
	 */
	private Network(Model model, String id, String name) {
		this.model = model;
		
		try {
			logicNetwork = model.getLogicModel().addExtendedBN(name, name);
			logicNetwork.setConnID(id);

		}
		catch (ExtendedBNException ex){
			// Should not really happen
			throw new AgenaRiskRuntimeException("Failed to create a new network", ex);
		}
		
	}
	
	/**
	 * Creates a Node and adds it to this Network
	 * @param id ID of the Node
	 * @param name name of the Node
	 * @param type type of the Node
	 * @return the created Node
	 * @throws NetworkException if Node creation failed
	 */
	public Node createNode(String id, String name, Ref.NODE_TYPE type) throws NetworkException {
		throw new NetworkException("Not implemented");
	}
	
	/**
	 * Creates a Node and adds it to this Network
	 * @param id ID of the Node
	 * @param type type of the Node
	 * @return the created Node
	 * @throws NetworkException if Node creation failed
	 */
	public Node createNode(String id, Ref.NODE_TYPE type) throws NetworkException {
		return createNode(id, id, type);
	}
	
	/**
	 * Creates a Node from its JSONObject specification and adds it to this Network
	 * @param json JSONObject with full Node's configuration
	 * @return the created Node
	 * @throws NetworkException if Node creation failed (Node with given ID already exists; or JSON configuration is missing required attributes)
	 */
	public Node createNode(JSONObject json) throws NetworkException {
		
		String id;
		
		try {
			id = json.getString(Ref.ID);
		}
		catch (JSONException ex){
			throw new NetworkException(JSONUtils.createMissingAttrMessage(ex), ex);
		}
		
		synchronized (IDContainer.class){
			if (nodes.containsKey(id)){
				throw new NetworkException("Node with id `" + id + "` already exists");
			}
			nodes.put(id, null);
		}
		
		Node node;
		
		try {
			node = Node.createNode(this, json);
			nodes.put(id, node);
		}
		catch (NodeException ex){
			nodes.remove(id);
			throw new NetworkException("Failed to add a node with id `" + id + "` to network `" + getId() + "`", ex);
		}
		
		return node;

	}

	/**
	 * Gets the ID of this Network
	 * @return the ID of this Network
	 */
	@Override
	public final String getId() {
		return getLogicNetwork().getConnID();
	}
	
	/**
	 * Changes the ID of this Network to the provided ID, if the new ID is not already taken
	 * Will lock IDContainer.class while doing so
	 * @param id the new ID
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
	 * Returns the underlying logical ExtendedBN network
	 * @return the underlying logical ExtendedBN network
	 */
	protected final ExtendedBN getLogicNetwork() {
		return logicNetwork;
	}

	/**
	 * Returns the Model that this Network belongs to
	 * @return the Model that this Network belongs to
	 */
	public final Model getModel() {
		return model;
	}
	
	/**
	 * Compares this Network object to another based on its underlying logic network IDs
	 * @param o another Network object
	 * @return String comparison of toStringExtra() of both Networks
	 */
	@Override
	public synchronized int compareTo(Network o) {
		// Sync to prevent wrong comparisons because ID was changed by another thread
		return this.toStringExtra().compareTo(o.toStringExtra());
	}
	
	/**
	 * Checks equality of a given object to this Network. Returns true if logic networks of both objects are the same
	 * @param obj The object to compare this Network against
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
	 * @return a hash code value for this object.
	 */
	@Override
	public int hashCode() {
		return System.identityHashCode(getLogicNetwork());
	}
	
	/**
	 * Returns 
	 * @return the ID of the underlying network surrounded by back ticks
	 */
	public String toStringExtra(){
		return "`" + getId() + "`";
	}

	/**
	 * Builds and returns a set of Networks, which are parents of this Network.
	 * Networks are connected with Links between their Nodes
	 * So for two Networks Net1 and Net2 and Nodes Node1 and Node2, where Node1 belongs to Net1 and Node2 belongs to Net2, and Node1 → Node2, Net2 is the child of Net1
	 * @return a set of Networks that are parents of this Network
	 */
	@Override
	public Set<Network> getParents() {
		Set<Network> nets = new HashSet<>();
		nodes.forEach((id,node) -> {
			node.getLinksIn().stream().map((link) -> link.getFromNode().getNetwork()).filter((net) -> (!Objects.equals(net, this))).forEach((net) -> {
				nets.add(net);
			});
		});
		return nets;
	}

	/**
	 * Builds and returns a set of Networks, which are children of this Network.
	 * Networks are connected with Links between their Nodes
	 * So for two Networks Net1 and Net2 and Nodes Node1 and Node2, where Node1 belongs to Net1 and Node2 belongs to Net2, and Node1 → Node2, Net2 is the child of Net1
	 * @return a set of Networks that are children of this Network
	 */
	@Override
	public Set<Network> getChildren() {
		Set<Network> nets = new HashSet<>();
		nodes.forEach((id,node) -> {
			node.getLinksOut().stream().map((link) -> link.getToNode().getNetwork()).filter((net) -> (!Objects.equals(net, this))).forEach((net) -> {
				nets.add(net);
			});
		});
		return nets;
	}

	/**
	 * Returns a copy of the incoming Links list
	 * @return a copy of the incoming Links list
	 */
	@Override
	public List<Link> getLinksIn() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	/**
	 * Returns a copy of the outgoing Links list
	 * @return a copy of the outgoing Links list
	 */
	@Override
	public List<Link> getLinksOut() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	/**
	 * Removes all links (if any exist) between the two networks
	 * @param network the Network to sever connections with
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
			Stream.of(node.getLinksOut().stream(), node.getLinksIn().stream()).flatMap(java.util.function.Function.identity()).forEach(link -> {
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
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public Map<String,? extends Identifiable> getIdMap(Class<? extends Identifiable> idClassType) throws NetworkException {
		if (Node.class.equals(idClassType)){
			return nodes;
		}
		throw new NetworkException("Invalid class type provided: "+idClassType);
	}

	/**
	 * @throws com.agenarisk.api.exception.NetworkException
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwIdExistsException(String id) throws NetworkException {
		throw new NetworkException("Node with id `" + id + "` already exists");
	}
	
	/**
	 * @throws com.agenarisk.api.exception.NetworkException
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwOldIdNullException(String id) throws NetworkException {
		throw new NetworkException("Can't change Node ID to `" + id + "` because the Node does not exist in this Network or old ID is null");
	}
	
	/**
	 * Gets Node from the Network by its unique ID
	 * @param id the ID of the Node
	 * @return the Node with the given ID or null if no such node exists in the Network
	 */
	public Node getNode(String id){
		return nodes.get(id);
	}

	/**
	 * Returns a copy of ID-Node map. Once generated, membership of this map is not maintained.
	 * @return copy of ID-Node map
	 */
	public Map<String, Node> getNodes() {
		return new HashMap<>(nodes);
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
	 * Sets the name of this Network
	 * @param name new name
	 */
	public void setName(String name){
		getLogicNetwork().getName().setShortDescription(name);
	}
	
	/**
	 * Gets the name of this Network
	 * @return the name of this Network
	 */
	public String getName(){
		return getLogicNetwork().getName().getShortDescription();
	}
	
	/**
	 * Sets the description of this Network
	 * @param description new description
	 */
	public void setDescription(String description){
		getLogicNetwork().getName().setLongDescription(description);
	}
	
	/**
	 * Gets the description of this Network
	 * @return the description of this Network
	 */
	public String getDescription(){
		return getLogicNetwork().getName().getLongDescription();
	}
}
