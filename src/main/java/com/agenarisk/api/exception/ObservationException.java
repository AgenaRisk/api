package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class ObservationException extends AgenaRiskRuntimeException {

	public ObservationException(String message) {
		super(message);
	}

	public ObservationException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
