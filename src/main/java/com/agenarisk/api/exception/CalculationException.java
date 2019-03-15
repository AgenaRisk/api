package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class CalculationException extends AgenaRiskException {

	public CalculationException(String message) {
		super(message);
	}

	public CalculationException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
