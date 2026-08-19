package com.agenarisk.api.tools.voi;

public class VoiAnalysisException extends Exception {

    public VoiAnalysisException() {}

    public VoiAnalysisException(String msg) {
        super(msg);
    }

    public VoiAnalysisException(Throwable throwable) {
        super(throwable);
    }

    public VoiAnalysisException(String msg, Throwable throwable) {
        super(msg, throwable);
    }
}
