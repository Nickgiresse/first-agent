package com.firstagent.backend.application.port.in;

import com.firstagent.backend.application.dto.ChallengeStartResponse;
import com.firstagent.backend.application.dto.ChallengeStatusResponse;
import com.firstagent.backend.application.dto.ChallengeVerifyResponse;
import java.util.List;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface LivenessUseCase {
    Mono<ChallengeStartResponse> start(String sessionToken);
    Mono<ChallengeVerifyResponse> verify(String sessionToken, String action, List<FilePart> frames);
    Mono<ChallengeStatusResponse> status(String sessionToken);
}
