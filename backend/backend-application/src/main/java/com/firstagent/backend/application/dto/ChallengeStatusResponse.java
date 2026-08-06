package com.firstagent.backend.application.dto;

import java.util.List;

public record ChallengeStatusResponse(
    String sessionId,
    List<String> actions,
    List<String> completedActions,
    List<String> remainingActions,
    boolean allActionsCompleted,
    boolean live
) {}
