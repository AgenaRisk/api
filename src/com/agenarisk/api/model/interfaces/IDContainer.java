package com.agenarisk.api.model.interfaces;

import com.agenarisk.api.exception.AgenaRiskException;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author Eugene Dementiev
 * @param <E> the type of AgenaRiskException thrown on error
 * @param <I> the type of contained object
 */
public interface IDContainer <E extends AgenaRiskException, I extends Identifiable> {
	
	/**
	 * 
	 * @return the map of Identifiable objects
	 */
	Map<String,I> getIdMap();
	
	/**
	 * Throws an Exception with message e.g. "Object with id `id` already exists", localised for the implementing class
	 * @param id ID of the existing object
	 */
	void throwIdExistsException(String id) throws E;
	
	/**
	 * Throws an Exception with message e.g. "No such object or old ID is null", localised for the implementing class
	 * @param id ID of the existing object
	 */
	void throwOldIdNullException(String id) throws E;
	
	/**
	 * Changes the ID of the Identifiable in some mapped reference of the IDContainer
	 * Does not actually modify Identifiable, which should be done externally
	 * @param identifiable the object which ID needs updating
	 * @param id new ID
	 * @return true if ID was changed and false if Identifiable is not contained in the map
	 * @throws E if ID already taken
	 */
	default boolean changeContainedId(I identifiable, String id) throws E {
		synchronized (IDContainer.class){
			if (getIdMap().containsKey(id)){
				throwIdExistsException(id);
			}
			
			String oldID = null;
			
			for(String key: getIdMap().keySet()){
				I idObject = getIdMap().get(key);
				if (Objects.equals(identifiable, idObject)){
					oldID = key;
					break;
				}
			}
			
			if (oldID == null){
				throwOldIdNullException(id);
			}
			
			getIdMap().remove(oldID);
			getIdMap().put(id, identifiable);
			
			return true;
		}
	}
}
