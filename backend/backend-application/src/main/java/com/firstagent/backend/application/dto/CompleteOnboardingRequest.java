package com.firstagent.backend.application.dto;

public record CompleteOnboardingRequest(
    String linkToken,
    String pin
) {}
