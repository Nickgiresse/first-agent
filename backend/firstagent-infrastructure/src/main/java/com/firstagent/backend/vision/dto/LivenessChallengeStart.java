package com.firstagent.backend.vision.dto;

import java.util.List;

public record LivenessChallengeStart(
    String sessionId, List<String> actions, int expiresInSeconds) {}
