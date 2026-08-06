package com.firstagent.backend.application.dto;

public record OnboardingCompletionResponse(
    String customerId,
    String status,
    String message
) {}
