package com.exe.astratarot.exception;

public class ReaderNotVerifiedException extends RuntimeException {
    
    public ReaderNotVerifiedException() {
        super("Tài khoản này chưa có hồ sơ Reader");
    }
}