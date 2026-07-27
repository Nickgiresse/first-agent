package com.firstagent.backend.common.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}