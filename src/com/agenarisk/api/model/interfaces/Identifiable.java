package com.agenarisk.api.model.interfaces;

import com.agenarisk.api.exception.AgenaRiskException;

/**
 *
 * @author Eugene Dementiev
 * @param <E> the type of AgenaRiskException thrown on error
 */
public interface Identifiable <E extends AgenaRiskException> {
	public String getId();
	public void setId(String id) throws E;
}
