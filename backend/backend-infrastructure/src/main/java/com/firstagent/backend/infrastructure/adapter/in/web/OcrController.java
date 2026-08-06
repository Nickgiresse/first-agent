package com.firstagent.backend.infrastructure.adapter.in.web;

import com.firstagent.backend.application.dto.OcrConfirmationRequest;
import com.firstagent.backend.application.dto.OcrExtractionResponse;
import com.firstagent.backend.application.port.in.OcrUseCase;
import com.firstagent.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/documents")
public class OcrController {

    private final OcrUseCase ocrUseCase;

    public OcrController(OcrUseCase ocrUseCase) {
        this.ocrUseCase = Objects.requireNonNull(ocrUseCase);
    }

    @PostMapping("/ocr/extract")
    public Mono<ResponseEntity<ApiResponse<OcrExtractionResponse>>> extract(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        return ocrUseCase.extract(sessionToken)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Extraction OCR réalisée avec succès")));
    }

    @GetMapping("/ocr")
    public Mono<ResponseEntity<ApiResponse<OcrExtractionResponse>>> get(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        return ocrUseCase.get(sessionToken)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PutMapping("/ocr")
    public Mono<ResponseEntity<ApiResponse<OcrExtractionResponse>>> confirm(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody OcrConfirmationRequest request
    ) {
        return ocrUseCase.confirm(sessionToken, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Données OCR confirmées avec succès")));
    }
}
