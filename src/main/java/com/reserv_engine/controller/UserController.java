package com.reserv_engine.controller;

import com.reserv_engine.dto.GrantRoleRequest;
import com.reserv_engine.dto.UserResponse;
import com.reserv_engine.entity.User;
import com.reserv_engine.security.SecurityUtils;
import com.reserv_engine.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> me() {
        String userId = SecurityUtils.currentUserId();
        User user = userService.getById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }
    @PostMapping("/me/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> grantMyRole(@Valid @RequestBody GrantRoleRequest request) {
        String userId = SecurityUtils.currentUserId();
        User user = userService.grantRole(userId, request.role());
        return ResponseEntity.ok(UserResponse.from(user));
    }
}