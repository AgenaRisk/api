package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class OutOfMemoryException extends CalculationException {

	public OutOfMemoryException(String message) {
		super(message);
	}

	public OutOfMemoryException(String message, Throwable cause) {
		super(message, cause);
	}

	public OutOfMemoryException(Throwable thrwbl) {
		super(thrwbl);
	}
}
