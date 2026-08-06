package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.onboarding.entity.OnboardingSession;

public interface OnboardingSessionService {

  OnboardingSession getValidSession(String sessionToken);

  void updateStatus(OnboardingSession session, OnboardingStatus newStatus);
}
