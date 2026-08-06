package com.firstagent.backend.application.dto;

import java.util.List;

public record ChallengeStartResponse(
    String sessionId,
    List<String> actions,
    long expiresInSeconds
) {}
