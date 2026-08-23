package com.reserv_engine.service;

import com.reserv_engine.core.domain.Role;

import com.reserv_engine.dto.CreateUserRequest;
import com.reserv_engine.entity.User;
import com.reserv_engine.exception.EmailAlreadyExistsException;
import com.reserv_engine.exception.UserNotFoundException;

import com.reserv_engine.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Set;


@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;



    @Transactional
    public User createUser(@Valid CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(Set.of(Role.CUSTOMER));

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User grantRole(String userId, Role role) {
        User user = getById(userId);
        user.getRoles().add(role);
        return userRepository.save(user);
    }
}