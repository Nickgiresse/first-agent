package com.firstagent.backend.liveness.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeStatusResponse {
    private String sessionId;
    private List<String> actions;
    private List<String> completedActions;
    private List<String> remainingActions;
    private boolean allActionsCompleted;
    private boolean live;
}
