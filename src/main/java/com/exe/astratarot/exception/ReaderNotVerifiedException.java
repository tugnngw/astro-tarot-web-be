package com.exe.astratarot.exception;

public class ReaderNotVerifiedException extends RuntimeException {
    
    public ReaderNotVerifiedException() {
        super("Reader profile not verified");
    }
}