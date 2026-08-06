package com.firstagent.backend.vision.dto;

import java.util.List;

public record LivenessChallengeStatus(
    String sessionId,
    List<String> actions,
    List<String> completedActions,
    List<String> remainingActions,
    boolean allActionsCompleted,
    String decision) {
  public boolean live() {
    return "LIVE".equals(decision);
  }
}
