package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class StateException extends AgenaRiskException {

	public StateException(String message) {
		super(message);
	}

	public StateException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
