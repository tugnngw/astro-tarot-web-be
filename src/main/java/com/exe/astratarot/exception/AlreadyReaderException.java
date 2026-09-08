package com.exe.astratarot.exception;

public class AlreadyReaderException extends RuntimeException {
    
    public AlreadyReaderException() {
        super("Tài khoản này đã là Reader rồi");
    }
}