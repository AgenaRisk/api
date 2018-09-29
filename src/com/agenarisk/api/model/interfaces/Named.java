package com.agenarisk.api.model.interfaces;


/**
 * 
 * @author Eugene Dementiev
 */
public interface Named {
	
	/**
	 * Sets the name of this Named object.
	 * 
	 * @param name new name
	 */
	public void setName(String name);
	
	/**
	 * Gets the name of this Named object.
	 * 
	 * @return the name of this Named object
	 */
	public String getName();
	
	/**
	 * Sets the description of this Named object.
	 * 
	 * @param description new description
	 */
	public void setDescription(String description);
	
	/**
	 * Gets the description of this Named object
	 * 
	 * @return the description of this Named object
	 */
	public String getDescription();
}
