package com.firstagent.backend.application.port.out;

import com.firstagent.backend.application.dto.FaceVerificationResponse;
import reactor.core.publisher.Mono;

public interface FaceVerificationPort {
    Mono<FaceVerificationResponse> compareFaces(byte[] sourceIdBytes, byte[] targetSelfieBytes);
}
