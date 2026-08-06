package com.firstagent.backend.application.port.in;

import com.firstagent.backend.application.dto.PinResetConfirmRequest;
import com.firstagent.backend.application.dto.PinResetRequest;
import com.firstagent.backend.application.dto.PinResetResponse;
import reactor.core.publisher.Mono;

public interface PinUseCase {
    Mono<PinResetResponse> requestReset(PinResetRequest request);
    Mono<Void> confirmReset(PinResetConfirmRequest request);
}
