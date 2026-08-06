package com.firstagent.backend.application.dto;

public record LinkVerificationResponse(
    boolean valid,
    String phone,
    String name,
    String accountNumber,
    String lang,
    boolean alreadyOnboarded
) {}
