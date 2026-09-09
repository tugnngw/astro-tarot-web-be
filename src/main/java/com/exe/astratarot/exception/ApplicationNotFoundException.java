package com.exe.astratarot.exception;

public class ApplicationNotFoundException extends RuntimeException {
    
    public ApplicationNotFoundException() {
        super("Không tìm thấy hồ sơ này");
    }
}