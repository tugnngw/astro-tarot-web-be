package com.exe.astratarot.exception;

public class AlreadyAppliedException extends RuntimeException {
    
    public AlreadyAppliedException() {
        super("User has already submitted a pending application");
    }
}