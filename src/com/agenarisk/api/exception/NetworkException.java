package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class NetworkException extends AgenaRiskException {

	public NetworkException(String message) {
		super(message);
	}

	public NetworkException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
