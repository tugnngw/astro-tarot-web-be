package com.exe.astratarot.exception;

public class AlreadyAppliedException extends RuntimeException {
    
    public AlreadyAppliedException() {
        super("Bạn đã có một hồ sơ đang chờ duyệt");
    }
}