package com.agenarisk.api.model.interfaces;

/**
 * Objects that have a unique ID and are members of some collection in a container class should implement this.
 * 
 * @author Eugene Dementiev
 * @param <E> the type of Exception thrown on error
 */
public interface Identifiable <E extends Exception> {
	
	/**
	 * Returns the ID of the object.
	 * 
	 * @return ID of the object
	 */
	public String getId();
	
	/**
	 * Changes the ID of the object.
	 * <br>
	 * It is expected that some constraints will be validated, e.g. whether another object with the same ID already exists.
	 * 
	 * @param id the new ID
	 * @throws E if some constraint is violated
	 */
	public void setId(String id) throws E;
}
