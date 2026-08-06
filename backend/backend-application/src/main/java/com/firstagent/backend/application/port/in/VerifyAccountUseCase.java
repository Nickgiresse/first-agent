package com.firstagent.backend.application.port.in;

import com.firstagent.backend.application.dto.VerifyAccountRequest;
import com.firstagent.backend.application.dto.VerifyAccountResponse;
import reactor.core.publisher.Mono;

public interface VerifyAccountUseCase {
    Mono<VerifyAccountResponse> verifyAccount(VerifyAccountRequest request);
}
