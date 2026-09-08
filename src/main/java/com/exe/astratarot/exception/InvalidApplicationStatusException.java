package com.exe.astratarot.exception;

public class InvalidApplicationStatusException extends RuntimeException {
    
    public InvalidApplicationStatusException() {
        super("Application status is not pending");
    }
}