package com.firstagent.backend.common.exception;

import org.springframework.http.HttpStatus;

public class OnboardingSessionExpiredException extends BusinessException {
    public OnboardingSessionExpiredException(String message) {
        super(message, HttpStatus.GONE);
    }
}