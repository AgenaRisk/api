package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class NodeException extends AgenaRiskRuntimeException {

	public NodeException(String message) {
		super(message);
	}

	public NodeException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
