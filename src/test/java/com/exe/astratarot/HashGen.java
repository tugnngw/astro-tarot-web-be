package com.exe.astratarot;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGen {
    public static void main(String[] args) {
        System.out.println("HASH_VALUE:" + new BCryptPasswordEncoder().encode("admin123"));
    }
}