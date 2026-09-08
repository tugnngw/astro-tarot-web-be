package com.exe.astratarot.exception;

public class InvalidApplicationStatusException extends RuntimeException {
    
    public InvalidApplicationStatusException() {
        super("Hồ sơ này đã được xử lý, không duyệt lại được");
    }
}