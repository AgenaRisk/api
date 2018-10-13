/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package misc;

import com.agenarisk.api.exception.AgenaRiskException;
import com.agenarisk.api.exception.ModelException;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author Eugene Dementiev
 */
public class Generics {
	
	public interface Identifiable <E extends AgenaRiskException> {
		public String getId();
		public void setId(String id) throws E;
	}
	
	public interface IDContainer <E extends AgenaRiskException> {
		Map<String,? extends Identifiable> getIdMap(Class classType);
		
		default <I extends Identifiable> boolean changeContainedId(I identifiable, String id) throws E {
			Map map = getIdMap(identifiable.getClass());
			
			if (map.containsKey(id)){
				throwException();
			}
			
			Object oldID = null;
			
			for(Object key: map.keySet()){
				Object idObject = map.get(key);
				if (Objects.equals(identifiable, idObject)){
					oldID = key;
					break;
				}
			}
			
			map.remove(oldID);
			map.put(id, identifiable);
			
			return true;
		}
		
		void throwException() throws E;
	}
	
//	class Container implements IDContainer<ModelException>{
//		
//		public void throwException() throws ModelException {
//			throw new ModelException("asd");
//		}
//	}
}
