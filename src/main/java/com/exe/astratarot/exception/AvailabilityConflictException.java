package com.exe.astratarot.exception;

public class AvailabilityConflictException extends RuntimeException {
    
    public AvailabilityConflictException() {
        super("Time slot overlaps with existing availability");
    }
}