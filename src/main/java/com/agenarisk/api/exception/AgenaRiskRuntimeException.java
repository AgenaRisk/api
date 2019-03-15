package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class AgenaRiskRuntimeException extends RuntimeException {

	public AgenaRiskRuntimeException(String message) {
		super(message);
	}

	public AgenaRiskRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
