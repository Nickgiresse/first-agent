package com.firstagent.backend.application.dto;

public record VerifyAccountResponse(
    boolean eligible,
    String firstName,
    String lastName,
    String sessionToken,
    long expiresInSeconds
) {}
