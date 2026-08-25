package com.reserv_engine.security;

import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}