package com.exe.astratarot.exception;

public class ApplicationNotFoundException extends RuntimeException {
    
    public ApplicationNotFoundException() {
        super("Application not found");
    }
}