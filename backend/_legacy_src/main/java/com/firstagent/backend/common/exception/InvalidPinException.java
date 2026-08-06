package com.firstagent.backend.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidPinException extends BusinessException {
    public InvalidPinException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}