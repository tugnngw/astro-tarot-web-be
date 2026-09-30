package com.exe.astratarot.config.exception;

public class QuotaExceededException extends com.exe.astratarot.exception.QuotaExceededException {

    public QuotaExceededException(String message) {
        super(message);
    }

    public QuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
