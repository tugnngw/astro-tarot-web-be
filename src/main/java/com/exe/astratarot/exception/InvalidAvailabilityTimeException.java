package com.exe.astratarot.exception;

public class InvalidAvailabilityTimeException extends RuntimeException {
    
    public InvalidAvailabilityTimeException() {
        super("Giờ kết thúc phải sau giờ bắt đầu");
    }
}