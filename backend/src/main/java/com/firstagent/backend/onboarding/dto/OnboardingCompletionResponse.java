package com.firstagent.backend.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingCompletionResponse {

    private UUID customerId;
    private String firstName;
    private String lastName;
    private String message;
}