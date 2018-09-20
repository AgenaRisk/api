package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class FileIOException extends AgenaRiskException {

	public FileIOException(String message) {
		super(message);
	}

	public FileIOException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
