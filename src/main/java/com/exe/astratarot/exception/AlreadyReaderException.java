package com.exe.astratarot.exception;

public class AlreadyReaderException extends RuntimeException {
    
    public AlreadyReaderException() {
        super("User is already a reader");
    }
}