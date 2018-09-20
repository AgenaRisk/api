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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import com.agenarisk.api.Ref;

/**
 *
 * @author Eugene Dementiev
 */
public class Network implements Networked<Network>, Comparable<Network>, Identifiable<NetworkException>, IDContainer<NetworkException, Node>, Storable {
	
	private final Model model;
	
	private final ExtendedBN logicNetwork;
	
	private final Map<String, Node> nodes = Collections.synchronizedMap(new HashMap<>());
	
	protected static Network createNetwork(Model model, String id, String name) {
		return new Network(model, id, name);
	}
	
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
	
	public Node addNode(String id, String name, Ref.NODE_TYPE type) throws NodeException {
		throw new NodeException("Not implemented");
	}
	
	public Node addNode(JSONObject json) throws NetworkException {
		
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

	@Override
	public final String getId() {
		return getLogicNetwork().getConnID();
	}
	
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

	protected final ExtendedBN getLogicNetwork() {
		return logicNetwork;
	}

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

	@Override
	public List<Link> getLinksIn() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

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
	public Map<String, Node> getIDMap() {
		return nodes;
	}

	/**
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwIDExistsException(String id) throws NetworkException {
		throw new NetworkException("Node with id `" + id + "` already exists");
	}
	
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

	@Override
	public JSONObject toJSON() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

}
