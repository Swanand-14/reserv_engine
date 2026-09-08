package com.reserv_engine.controller;

import com.reserv_engine.config.CookieProperties;
import com.reserv_engine.dto.CreateUserRequest;
import com.reserv_engine.dto.LoginRequest;
import com.reserv_engine.dto.LoginResponse;
import com.reserv_engine.dto.UserResponse;
import com.reserv_engine.entity.User;
import com.reserv_engine.security.JwtService;
import com.reserv_engine.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private static final String COOKIE_NAME = "jwt";
    private final CookieProperties cookieProperties;




    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.email(), request.password());

        String token = jwtService.generateToken(user);
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/")
                .maxAge(jwtService.getExpirationSeconds())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(UserResponse.from(user));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/")
                .maxAge(0) // instructs the browser to delete it immediately
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}