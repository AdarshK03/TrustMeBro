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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User with email " + request.getEmail() + " already exists");
        }

        String rawPassword = (request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword()
                : "Password@123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = new User(request.getName(), request.getEmail(), request.getRole(), encodedPassword);
        User savedUser = userRepository.save(user);
        log.info("Created user id={} email={} role={}", savedUser.getId(), savedUser.getEmail(), savedUser.getRole());
        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        log.debug("Authenticating user with email: {}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Authentication failed for email: {} - password mismatch", request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtTokenService.generateToken(user);
        long expiresIn = jwtTokenService.getExpirationMs();
        log.info("User authenticated successfully: id={}, email={}, role={}", user.getId(), user.getEmail(), user.getRole());

        return new LoginResponse(token, "Bearer", expiresIn, UserResponse.fromEntity(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
