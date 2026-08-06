package com.firstagent.backend.application.dto;

import java.util.List;

public record ChallengeVerifyResponse(
    String sessionId,
    String action,
    boolean actionCompleted,
    List<String> completedActions,
    List<String> remainingActions,
    boolean allActionsCompleted
) {}
