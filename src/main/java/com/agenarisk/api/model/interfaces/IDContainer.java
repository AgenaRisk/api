package com.agenarisk.api.model.interfaces;

import com.agenarisk.api.exception.AgenaRiskException;
import com.agenarisk.api.model.field.Id;
import java.util.Map;
import java.util.Objects;

/**
 * Classes that have a collection of some identifiable objects that have a unique ID should implement this.
 * 
 * @author Eugene Dementiev
 * @param <E> the type of AgenaRiskException thrown on error
 */
public interface IDContainer <E extends AgenaRiskException> {
	
	/**
	 * Returns the map of identifiable items based on.
	 * 
	 * @param idClassType the class of identifiable object
	 * @return ID-Identifiable map
	 * @throws E if no collection of type idClassType is registered with the implementing class
	 */
	Map<Id,? extends Identifiable> getIdMap(Class<? extends Identifiable> idClassType) throws E;
	
	/**
	 * Throws an Exception with message e.g. "Object with id `id` already exists", localised for the implementing class.
	 * 
	 * @param id ID of the existing object
	 * @throws E when invoked
	 */
	void throwIdExistsException(String id) throws E;
	
	/**
	 * Throws an Exception with message e.g. "No such object or old ID is null", localised for the implementing class.
	 * 
	 * @param id ID of the existing object
	 * @throws E when invoked
	 */
	void throwOldIdNullException(String id) throws E;
	
	/**
	 * Changes the ID of the Identifiable in some mapped reference of the IDContainer.
	 * <br>
	 * Does not actually modify Identifiable, which should be done externally.
	 * 
	 * @param <I> Type of the identifiable object
	 * @param identifiable the object which ID needs updating
	 * @param id new ID
	 * @return true if ID was changed and false if Identifiable is not contained in the map
	 * @throws E if ID already taken
	 * @deprecated For internal use only. You will break referential integrity by using this method directly.
	 */
	@Deprecated
	default <I extends Identifiable> boolean changeContainedId(I identifiable, String id) throws E {
		synchronized (IDContainer.class){
			
			Map map = getIdMap(identifiable.getClass());
			
			if (map.containsKey(new Id(id))){
				throwIdExistsException(id);
			}
			
			Object oldID = null;
			
			for(Object key: map.keySet()){
				Object idObject = map.get(key);
				if (Objects.equals(identifiable, idObject)){
					oldID = key;
					break;
				}
			}
			
			if (oldID == null){
				throwOldIdNullException(id);
			}
			
			map.remove(oldID);
			map.put(new Id(id), identifiable);
			
			return true;
		}
	}
}
