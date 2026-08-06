package com.firstagent.backend.application.port.in;

import com.firstagent.backend.application.dto.FaceVerificationResponse;
import reactor.core.publisher.Mono;

public interface FaceVerificationUseCase {
    Mono<FaceVerificationResponse> verify(String sessionToken);
    Mono<FaceVerificationResponse> get(String sessionToken);
}
