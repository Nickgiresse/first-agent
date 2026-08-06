package com.firstagent.backend.infrastructure.adapter.in.web;

import com.firstagent.backend.application.dto.FaceVerificationResponse;
import com.firstagent.backend.application.port.in.FaceVerificationUseCase;
import com.firstagent.backend.common.dto.ApiResponse;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/documents")
public class FaceVerificationController {

    private final FaceVerificationUseCase faceVerificationUseCase;

    public FaceVerificationController(FaceVerificationUseCase faceVerificationUseCase) {
        this.faceVerificationUseCase = Objects.requireNonNull(faceVerificationUseCase);
    }

    @PostMapping("/face-verification/verify")
    public Mono<ResponseEntity<ApiResponse<FaceVerificationResponse>>> verify(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        return faceVerificationUseCase.verify(sessionToken)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Vérification faciale réussie")));
    }

    @GetMapping("/face-verification")
    public Mono<ResponseEntity<ApiResponse<FaceVerificationResponse>>> get(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        return faceVerificationUseCase.get(sessionToken)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }
}
