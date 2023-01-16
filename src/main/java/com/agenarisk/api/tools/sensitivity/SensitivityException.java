package com.agenarisk.api.tools.sensitivity;

import com.agenarisk.api.tools.calculator.*;

/**
 *
 * @author Eugene Dementiev
 */
public class SensitivityException extends RuntimeException {

	public SensitivityException() {
	}

	public SensitivityException(String message) {
		super(message);
	}

	public SensitivityException(String message, Throwable cause) {
		super(message, cause);
	}

	public SensitivityException(Throwable cause) {
		super(cause);
	}

}
