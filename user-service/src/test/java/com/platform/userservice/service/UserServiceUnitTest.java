package com.platform.userservice.service;

import com.platform.userservice.dto.LoginRequest;
import com.platform.userservice.dto.LoginResponse;
import com.platform.userservice.dto.UserCreateRequest;
import com.platform.userservice.dto.UserResponse;
import com.platform.userservice.exception.DuplicateResourceException;
import com.platform.userservice.exception.InvalidCredentialsException;
import com.platform.userservice.exception.ResourceNotFoundException;
import com.platform.userservice.model.User;
import com.platform.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("Alice", "alice@example.com", "CUSTOMER", "$2a$10$encodedPassword");
        sampleUser.setId(1L);
        sampleUser.setCreatedAt(Instant.now());
        sampleUser.setUpdatedAt(Instant.now());
    }

    @Test
    void createUser_Success() {
        UserCreateRequest request = new UserCreateRequest("Alice", "alice@example.com", "CUSTOMER");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Alice", response.getName());
        assertEquals("alice@example.com", response.getEmail());
        verify(userRepository, times(1)).existsByEmail("alice@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void createUser_DuplicateEmail_ThrowsDuplicateResourceException() {
        UserCreateRequest request = new UserCreateRequest("Alice", "alice@example.com", "CUSTOMER");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.createUser(request)
        );

        assertTrue(exception.getMessage().contains("already exists"));
        verify(userRepository, times(1)).existsByEmail("alice@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getUserById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Alice", response.getName());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void getUserById_NotFound_ThrowsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserById(999L)
        );

        assertTrue(exception.getMessage().contains("User not found with id: 999"));
        verify(userRepository, times(1)).findById(999L);
    }

    @Test
    void getAllUsers_ReturnsList() {
        User user2 = new User("Bob", "bob@example.com", "CUSTOMER");
        user2.setId(2L);
        when(userRepository.findAll()).thenReturn(List.of(sampleUser, user2));

        List<UserResponse> responses = userService.getAllUsers();

        assertEquals(2, responses.size());
        assertEquals("Alice", responses.get(0).getName());
        assertEquals("Bob", responses.get(1).getName());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password@123");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password@123", "$2a$10$encodedPassword")).thenReturn(true);
        when(jwtTokenService.generateToken(sampleUser)).thenReturn("mock.jwt.token");
        when(jwtTokenService.getExpirationMs()).thenReturn(86400000L);

        LoginResponse response = userService.login(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(86400000L, response.getExpiresIn());
        assertEquals("Alice", response.getUser().getName());
        assertEquals("alice@example.com", response.getUser().getEmail());
    }

    @Test
    void login_UserNotFound_ThrowsInvalidCredentials() {
        LoginRequest request = new LoginRequest("unknown@example.com", "Password@123");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> userService.login(request));
    }

    @Test
    void login_WrongPassword_ThrowsInvalidCredentials() {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$10$encodedPassword")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.login(request));
    }
}
