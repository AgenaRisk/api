package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.model.interfaces.Storable;
import com.singularsys.jep.JepException;
import java.util.List;
import java.util.Objects;
import org.json.JSONObject;
import uk.co.agena.minerva.model.corebn.CoreBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;

/**
 * Link represents a link between two nodes.
 * 
 * @author Eugene Dementiev
 */
public class Link implements Comparable<Link>, Storable {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		links,
		link,
		parent,
		child
	}
	
	/**
	 * Source and target Nodes
	 */
	private final Node fromNode, toNode;
	
	/**
	 * This stores graphics for the object, and should be set on model load, and then saved on model save
	 */
	private JSONObject graphics;
	
	/**
	 * Creates the Link object. Only for use by Link class and its subclasses.
	 * 
	 * @param fromNode source Node of the Link
	 * @param toNode target Node of the Link
	 * @deprecated For internal use only. Use createLink() instead
	 */
	@Deprecated
	protected Link(Node fromNode, Node toNode){
		this.fromNode = fromNode;
		this.toNode = toNode;
		
	}
	
	/**
	 * Creates a Link object without manipulating the nodes themselves.
	 * 
	 * @param fromNode source Node of the Link
	 * @param toNode target Node of the Link
	 * @return the created Link object
	 * @throws LinkException if Nodes are in different Network (in that case CrossNetworkLink should haves been used)
	 */
	protected static Link createLink(Node fromNode, Node toNode) throws LinkException{
		if (!Objects.equals(fromNode.getNetwork(), toNode.getNetwork())){
			throw new LinkException("Nodes must be in the same network for simple link");
		}
		return new Link(fromNode, toNode);
	}
	
	/**
	 * This will create a link in the underlying logic.
	 * <br>
	 * The underlying table of the child node will be reset to some default value.
	 * 
	 * @throws LinkException if logical link was not created
	 */
	protected void createLogicLink() throws LinkException{
		
		try {
			List<ExtendedNode> existing = fromNode.getNetwork().getLogicNetwork().getChildNodes(fromNode.getLogicNode());
			if (existing.contains(toNode.getLogicNode())){
				throw new LinkException("Link already exists");
			}
			
			if (toNode.getLogicNode().isConnectableInputNode() && toNode.getLinksIn().isEmpty()){
				// Target node is marked as input node, but has no incoming links; unmark
				toNode.getLogicNode().setConnectableInputNode(false);
			}
			
			boolean created = fromNode.getLogicNode().addChild(toNode.getLogicNode());
			
			if (!created){
				throw new LinkException("Logic network failure while linking nodes");
			}
		}
		catch(ExtendedBNException ex){
			if (ex.getCause() instanceof CoreBNException){
				// There seem to be more reasons for CoreBNException but actually it will only be thrown when these IDs are not found
				throw new LinkException("Logic node with ID `" + fromNode.getId() + "` or `" + toNode.getId() + "` not found", ex);
			}
		}
		
	}
	
	/**
	 * Destroys the underlying logic link
	 * <br>
	 * Has no effect if the underlying logical network does not contain either of the nodes.
	 */
	protected void destroyLogicLink() {
		try {
			fromNode.getNetwork().getLogicNetwork().removeRelationship(fromNode.getLogicNode(), toNode.getLogicNode(), false);
		}
		catch (ExtendedBNException ex){
			if (ex.getCause() instanceof CoreBNException){
				throw new LinkException("Logic node with ID `" + fromNode.getId() + "` or `" + toNode.getId() + "` not found in network `" + fromNode.getNetwork().getId() + "`", ex);
			}
			throw new LinkException("CoreBN missing");
		}
		catch (JepException ex){
			// Do nothing as we don' care about expression validity here
		}
	}

	/**
	 * Returns the source Link Node.
	 * 
	 * @return the source Link Node
	 */
	public Node getFromNode() {
		return fromNode;
	}

	/**
	 * Returns the target Link Node.
	 * 
	 * @return the target Link Node 
	 */
	public Node getToNode() {
		return toNode;
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
	 * Returns the string representation of the Link as: `Net1`.`Node1` → `Net2`.`Node2`.
	 * 
	 * @return string representation of the Link
	 */
	public String toStringExtra(){
		return fromNode.toStringExtra() + " -> " + toNode.toStringExtra();
	}

	/**
	 * Compares this Link object to another based on its underlying logic network and node IDs.
	 * 
	 * @param o another Link object
	 * @return String comparison of toStringExtra() of both Links
	 */
	@Override
	public synchronized int compareTo(Link o) {
		// Sync to prevent wrong comparisons because ID was changed by another thread
		return this.toStringExtra().compareTo(o.toStringExtra());
	}
	
	/**
	 * Checks equality of a given object to this Link.
	 * 
	 * @param obj The object to compare this Link against
	 * @return true if the two object references are the same
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Link)){
			return false;
		}
		
		return this == obj;
	}

	/**
	 * Returns a hash code value for this object.
	 * 
	 * @return a hash code value for this object.
	 */
	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}
	
	/**
	 * Stores Link's graphics.
	 * 
	 * @param graphics the graphics json to store
	 */
	protected void setGraphics(JSONObject graphics) {
		this.graphics = graphics;
	}

	/**
	 * Creates a JSON representing this Link, ready for file storage.
	 * 
	 * @return JSONObject representing this Link
	 */
	@Override
	public JSONObject toJSONObject() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
}
