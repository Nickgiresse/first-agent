package com.firstagent.backend.vision.dto;

import java.util.List;

public record LivenessChallengeVerify(
    String sessionId,
    String action,
    boolean actionCompleted,
    List<String> completedActions,
    List<String> remainingActions,
    boolean allActionsCompleted) {}
