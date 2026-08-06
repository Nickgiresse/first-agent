package com.firstagent.backend.liveness.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeVerifyResponse {
  private String sessionId;
  private String action;
  private boolean actionCompleted;
  private List<String> completedActions;
  private List<String> remainingActions;
  private boolean allActionsCompleted;
}
