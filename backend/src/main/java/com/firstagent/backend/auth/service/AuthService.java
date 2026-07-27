package com.firstagent.backend.auth.service;

import com.firstagent.backend.auth.dto.LoginRequest;
import com.firstagent.backend.auth.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}