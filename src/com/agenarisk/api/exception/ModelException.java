package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class ModelException extends AgenaRiskException {

	public ModelException(String message) {
		super(message);
	}

	public ModelException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
