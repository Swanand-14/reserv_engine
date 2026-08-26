package com.reserv_engine;

import com.reserv_engine.core.domain.Role;

import com.reserv_engine.dto.CreateUserRequest;
import com.reserv_engine.entity.User;
import com.reserv_engine.exception.EmailAlreadyExistsException;

import com.reserv_engine.repository.UserRepository;
import com.reserv_engine.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(userRepository, passwordEncoder,authenticationManager);
    }

    @Test
    void createUser_assignsCustomerRoleAndHashesPassword() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser(new CreateUserRequest("a@b.com", "password123"));

        assertThat(result.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(result.getPasswordHash()).isEqualTo("hashed");

    }

    @Test
    void createUser_duplicateEmail_throws() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest("a@b.com", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }





}