package com.reserv_engine.controller;

import com.reserv_engine.dto.CreateUserRequest;
import com.reserv_engine.dto.LoginRequest;
import com.reserv_engine.dto.LoginResponse;
import com.reserv_engine.dto.UserResponse;
import com.reserv_engine.entity.User;
import com.reserv_engine.security.JwtService;
import com.reserv_engine.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;




    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.email(), request.password());

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token,UserResponse.from(user)));
    }
}