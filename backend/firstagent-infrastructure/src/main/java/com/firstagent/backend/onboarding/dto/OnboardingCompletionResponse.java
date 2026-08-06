package com.firstagent.backend.onboarding.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingCompletionResponse {

  private UUID customerId;
  private String firstName;
  private String lastName;
  private String message;
  private boolean requiresManualReview;
}
