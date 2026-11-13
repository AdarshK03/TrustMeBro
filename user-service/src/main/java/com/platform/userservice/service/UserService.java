package com.platform.userservice.service;

import com.platform.userservice.dto.LoginRequest;
import com.platform.userservice.dto.LoginResponse;
import com.platform.userservice.dto.UserCreateRequest;
import com.platform.userservice.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse createUser(UserCreateRequest request);
    UserResponse getUserById(Long id);
    List<UserResponse> getAllUsers();
    LoginResponse login(LoginRequest request);
}
