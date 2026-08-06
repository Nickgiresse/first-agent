package com.firstagent.backend.application.port.in;

import com.firstagent.backend.application.dto.OcrConfirmationRequest;
import com.firstagent.backend.application.dto.OcrExtractionResponse;
import reactor.core.publisher.Mono;

public interface OcrUseCase {
    Mono<OcrExtractionResponse> extract(String sessionToken);
    Mono<OcrExtractionResponse> get(String sessionToken);
    Mono<OcrExtractionResponse> confirm(String sessionToken, OcrConfirmationRequest request);
}
