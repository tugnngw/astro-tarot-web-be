package com.exe.astratarot.exception;

public class AvailabilityConflictException extends RuntimeException {
    
    public AvailabilityConflictException() {
        super("Khung giờ này trùng với một khung giờ đã khai báo");
    }
}