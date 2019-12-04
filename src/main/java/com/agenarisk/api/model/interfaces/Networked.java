package com.agenarisk.api.model.interfaces;

import com.agenarisk.api.model.Link;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An interface for objects that can be connected into a network of objects (e.g. Nodes can be connected within a Network, or Networks can be connected within a Model).
 * 
 * @author Eugene Dementiev
 * @param <N> specific class implementing Networked, e.g. Network or Node
 */
public interface Networked<N extends Networked> {
	
	/**
	 * Checks recursively whether any of the links are incoming from the provided ancestor.
	 * 
	 * @param ancestor ancestor object to look for
	 * @return true if ancestor is direct parent or a recursive ancestor
	 */
	default public boolean hasAncestor(N ancestor){
		return getParents().stream().anyMatch((n) -> (Objects.equals(n, ancestor) || n.hasAncestor(ancestor)));
	}
	
	/**
	 * Checks recursively whether any of the links are outgoing to the provided descendant.
	 * 
	 * @param descendant descendant object to look for
	 * @return true if descendant is direct child or a recursive descendant
	 */
	default public boolean hasDescendant(N descendant){
		return getChildren().stream().anyMatch((n) -> (Objects.equals(n, descendant) || n.hasDescendant(descendant)));
	}
	
	/**
	 * Builds and returns a set of parents, which is valid at the time of request. This set will not reflect any membership changes made afterwards.
	 * 
	 * @return a set of Networked parents
	 */
	public Set<N> getParents();
	
	/**
	 * Builds and returns a set of children, which is valid at the time of request. This set will not reflect any membership changes made afterwards.
	 * 
	 * @return a set of Networked children
	 */
	public Set<N> getChildren();
	
	/**
	 * Returns a copy of the list of incoming Links. The membership is only guaranteed to be valid at the time of request and is not maintained.
	 * 
	 * @return list of incoming Links
	 */
	public List<Link> getLinksIn();
	
	/**
	 * Returns a copy of the list of outgoing Links. The membership is only guaranteed to be valid at the time of request and is not maintained.
	 * 
	 * @return list of outgoing Links
	 */
	public List<Link> getLinksOut();
	
	/**
	 * Breaks the Link between this Networked object and the one provided, regardless of which one is the parent and which is the child.
	 * 
	 * @param networked the Networked object to break a link with
	 * @return true if the Link was actually removed and false if there was no such Link to begin with
	 */
	public boolean unlink(N networked);
}
