package com.exe.astratarot.exception;

public class InvalidAvailabilityTimeException extends RuntimeException {
    
    public InvalidAvailabilityTimeException() {
        super("End time must be after start time");
    }
}