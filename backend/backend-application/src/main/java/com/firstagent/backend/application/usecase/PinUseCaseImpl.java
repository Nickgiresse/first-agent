package com.firstagent.backend.application.usecase;

import com.firstagent.backend.application.dto.PinResetConfirmRequest;
import com.firstagent.backend.application.dto.PinResetRequest;
import com.firstagent.backend.application.dto.PinResetResponse;
import com.firstagent.backend.application.port.in.PinUseCase;
import reactor.core.publisher.Mono;

public class PinUseCaseImpl implements PinUseCase {

    @Override
    public Mono<PinResetResponse> requestReset(PinResetRequest request) {
        return Mono.just(new PinResetResponse(
                true,
                false,
                "Un lien de réinitialisation vous a été envoyé par e-mail"
        ));
    }

    @Override
    public Mono<Void> confirmReset(PinResetConfirmRequest request) {
        return Mono.empty();
    }
}
