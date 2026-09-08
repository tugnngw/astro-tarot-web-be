package com.exe.astratarot.security;

public final class SecurityPermissions {
    
    // Auth & Generic User
    public static final String USER_BASIC = "USER_BASIC";
    
    // Reader Operations
    public static final String READER_APPLY = "READER_APPLY";
    public static final String READER_MANAGE_PROFILE = "READER_MANAGE_PROFILE";
    
    // Admin Operations
    public static final String ADMIN_READERS_VIEW = "ADMIN_READERS_VIEW";
    public static final String ADMIN_READERS_REVIEW = "ADMIN_READERS_REVIEW";
    public static final String ADMIN_MANAGE_AVAILABILITY = "ADMIN_MANAGE_AVAILABILITY";
    public static final String ADMIN_MANAGE_UNAVAILABLE = "ADMIN_MANAGE_UNAVAILABLE";
    public static final String ADMIN_VIEW_PUBLIC_READERS = "ADMIN_VIEW_PUBLIC_READERS";

    private SecurityPermissions() {}
}