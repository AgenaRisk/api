package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class DataSetException extends AgenaRiskRuntimeException {

	public DataSetException(String message) {
		super(message);
	}

	public DataSetException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
