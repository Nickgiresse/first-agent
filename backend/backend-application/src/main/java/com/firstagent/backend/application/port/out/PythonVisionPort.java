package com.firstagent.backend.application.port.out;

import com.firstagent.backend.application.dto.OcrExtractionResponse;
import reactor.core.publisher.Mono;

public interface PythonVisionPort {
    Mono<OcrExtractionResponse> extractDocument(byte[] frontBytes, byte[] backBytes);
}
