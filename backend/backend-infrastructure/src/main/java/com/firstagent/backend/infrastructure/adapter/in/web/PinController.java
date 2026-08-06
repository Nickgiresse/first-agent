package com.firstagent.backend.infrastructure.adapter.in.web;

import com.firstagent.backend.application.dto.PinResetConfirmRequest;
import com.firstagent.backend.application.dto.PinResetRequest;
import com.firstagent.backend.application.dto.PinResetResponse;
import com.firstagent.backend.application.port.in.PinUseCase;
import com.firstagent.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/pin")
public class PinController {

    private final PinUseCase pinUseCase;

    public PinController(PinUseCase pinUseCase) {
        this.pinUseCase = Objects.requireNonNull(pinUseCase);
    }

    @PostMapping("/reset/request")
    public Mono<ResponseEntity<ApiResponse<PinResetResponse>>> requestReset(
            @Valid @RequestBody PinResetRequest request
    ) {
        return pinUseCase.requestReset(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, response.message())));
    }

    @PostMapping("/reset/confirm")
    public Mono<ResponseEntity<ApiResponse<Void>>> confirmReset(
            @Valid @RequestBody PinResetConfirmRequest request
    ) {
        return pinUseCase.confirmReset(request)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success(null, "PIN réinitialisé avec succès"))));
    }
}
