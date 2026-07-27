package com.firstagent.backend.auth.dto;

import com.firstagent.backend.onboarding.dto.CustomerResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresInSeconds;
    private CustomerResponse customer;
}
