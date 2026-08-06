package com.firstagent.backend.infrastructure.adapter.in.web;

import com.firstagent.backend.application.dto.ChallengeStartResponse;
import com.firstagent.backend.application.dto.ChallengeStatusResponse;
import com.firstagent.backend.application.dto.ChallengeVerifyResponse;
import com.firstagent.backend.application.port.in.LivenessUseCase;
import com.firstagent.backend.common.dto.ApiResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/documents/liveness")
public class LivenessController {

    private final LivenessUseCase livenessUseCase;

    public LivenessController(LivenessUseCase livenessUseCase) {
        this.livenessUseCase = Objects.requireNonNull(livenessUseCase);
    }

    @PostMapping("/challenge/start")
    public Mono<ResponseEntity<ApiResponse<ChallengeStartResponse>>> start(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        return livenessUseCase.start(sessionToken)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Défi de vivacité démarré")));
    }

    @PostMapping(value = "/challenge/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<ChallengeVerifyResponse>>> verify(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            ServerWebExchange exchange
    ) {
        return exchange.getMultipartData().flatMap(multipart -> {
            List<Part> actionParts = multipart.get("action");
            String action = actionParts != null && !actionParts.isEmpty() && actionParts.get(0) instanceof FormFieldPart ffp
                    ? ffp.value() : "BLINK";

            List<Part> rawFrames = multipart.get("frames");
            List<FilePart> frames = rawFrames != null
                    ? rawFrames.stream().filter(p -> p instanceof FilePart).map(p -> (FilePart) p).toList()
                    : List.of();

            return livenessUseCase.verify(sessionToken, action, frames)
                    .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
        });
    }

    @GetMapping("/challenge/status")
    public Mono<ResponseEntity<ApiResponse<ChallengeStatusResponse>>> status(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        return livenessUseCase.status(sessionToken)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }
}
