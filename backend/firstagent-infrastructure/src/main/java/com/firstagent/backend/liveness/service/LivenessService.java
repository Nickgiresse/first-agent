package com.firstagent.backend.liveness.service;

import com.firstagent.backend.liveness.dto.ChallengeStartResponse;
import com.firstagent.backend.liveness.dto.ChallengeStatusResponse;
import com.firstagent.backend.liveness.dto.ChallengeVerifyResponse;

import java.util.List;

public interface LivenessService {

    ChallengeStartResponse startChallenge(String sessionToken);

    ChallengeVerifyResponse verifyAction(String sessionToken, String action, List<byte[]> frames);

    ChallengeStatusResponse getStatus(String sessionToken);

    boolean isLive(String sessionToken);
}
