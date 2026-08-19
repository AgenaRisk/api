package com.agenarisk.api.tools.hid;

public class HidException extends Exception {

    public HidException() {
    }

    public HidException(String message) {
        super(message);
    }

    public HidException(String message, Throwable cause) {
        super(message, cause);
    }

    public HidException(Throwable cause) {
        super(cause);
    }
}
