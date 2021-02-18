package com.agenarisk.api.exception;

/**
 *
 * @author Eugene Dementiev
 */
public class AgenaRiskException extends Exception {

	public AgenaRiskException(String message) {
		super(message);
	}

	public AgenaRiskException(String message, Throwable cause) {
		super(message, cause);
	}

	public AgenaRiskException(Throwable thrwbl) {
		super(thrwbl);
	}

}
