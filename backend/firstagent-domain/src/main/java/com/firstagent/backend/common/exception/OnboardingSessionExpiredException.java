package com.firstagent.backend.common.exception;

/** Session d'onboarding expirée. */
public class OnboardingSessionExpiredException extends BusinessException {

  public OnboardingSessionExpiredException(String message) {
    super(message, TypeErreurMetier.EXPIRE);
  }

  public OnboardingSessionExpiredException(String message, String codeRegle) {
    super(message, TypeErreurMetier.EXPIRE, codeRegle);
  }
}
