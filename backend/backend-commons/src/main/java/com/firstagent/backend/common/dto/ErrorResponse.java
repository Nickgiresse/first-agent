package com.firstagent.backend.common.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ErrorResponse {

    private final boolean success;
    private final String errorCode;
    private final String message;
    private final List<String> details;
    private final LocalDateTime timestamp;

    public ErrorResponse() {
        this.success = false;
        this.errorCode = null;
        this.message = null;
        this.details = null;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(boolean success, String errorCode, String message, List<String> details, LocalDateTime timestamp) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDetails() {
        return details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
