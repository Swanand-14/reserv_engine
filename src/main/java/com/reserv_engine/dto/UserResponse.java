package com.reserv_engine.dto;

import com.reserv_engine.core.domain.Role;
import com.reserv_engine.entity.User;

import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
        String id,
        String email,
        Set<Role> roles,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRoles(), user.getCreatedAt());
    }
}