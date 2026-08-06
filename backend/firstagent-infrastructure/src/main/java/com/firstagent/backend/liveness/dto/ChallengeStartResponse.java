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
public class ChallengeStartResponse {
  private String sessionId;
  private List<String> actions;
  private int expiresInSeconds;
}
