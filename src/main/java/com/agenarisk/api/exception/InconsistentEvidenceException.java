package com.agenarisk.api.exception;

/**
 * Thrown when specifically inconsistent evidence was detected and caused a calculation failure.
 * 
 * @author Eugene Dementiev
 */
public class InconsistentEvidenceException extends CalculationException {

	public InconsistentEvidenceException(String message) {
		super(message);
	}

	public InconsistentEvidenceException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
