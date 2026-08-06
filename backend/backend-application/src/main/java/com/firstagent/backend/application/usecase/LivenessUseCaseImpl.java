package com.firstagent.backend.application.usecase;

import com.firstagent.backend.application.dto.ChallengeStartResponse;
import com.firstagent.backend.application.dto.ChallengeStatusResponse;
import com.firstagent.backend.application.dto.ChallengeVerifyResponse;
import com.firstagent.backend.application.port.in.LivenessUseCase;
import com.firstagent.backend.application.port.in.OcrUseCase;
import com.firstagent.backend.domain.exception.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public class LivenessUseCaseImpl implements LivenessUseCase {

    private static final Logger log = LoggerFactory.getLogger(LivenessUseCaseImpl.class);
    private static final List<String> REQUIRED_ACTIONS = List.of("BLINK", "SMILE", "TURN_LEFT");

    private final OcrUseCase ocrUseCase;
    private final Map<String, Set<String>> completedActionsMap = new ConcurrentHashMap<>();

    public LivenessUseCaseImpl() {
        this.ocrUseCase = null;
    }

    public LivenessUseCaseImpl(OcrUseCase ocrUseCase) {
        this.ocrUseCase = ocrUseCase;
    }

    @Override
    public Mono<ChallengeStartResponse> start(String sessionToken) {
        String key = (sessionToken != null && !sessionToken.isBlank()) ? sessionToken : "DEFAULT";
        completedActionsMap.put(key, ConcurrentHashMap.newKeySet());
        log.info("Démarrage du défi de vivacité pour key [{}], actions requises: {}", key, REQUIRED_ACTIONS);

        if (ocrUseCase == null) {
            return Mono.just(new ChallengeStartResponse(
                    UUID.randomUUID().toString(),
                    REQUIRED_ACTIONS,
                    300
            ));
        }

        return ocrUseCase.get(sessionToken)
                .flatMap(ocr -> {
                    if (ocr == null || !"CONFIRMED".equalsIgnoreCase(ocr.status())) {
                        return Mono.error(new BusinessRuleException(
                                "RG-LIV-001",
                                "La vérification du document d'identité (OCR) et la confirmation des données sont obligatoires avant d'accéder à la reconnaissance faciale"
                        ));
                    }
                    return Mono.just(new ChallengeStartResponse(
                            UUID.randomUUID().toString(),
                            REQUIRED_ACTIONS,
                            300
                    ));
                })
                .switchIfEmpty(Mono.error(new BusinessRuleException(
                        "RG-LIV-001",
                        "La vérification du document d'identité (OCR) et la confirmation des données sont obligatoires avant d'accéder à la reconnaissance faciale"
                )));
    }

    @Override
    public Mono<ChallengeVerifyResponse> verify(String sessionToken, String action, List<FilePart> frames) {
        String key = (sessionToken != null && !sessionToken.isBlank()) ? sessionToken : "DEFAULT";
        Set<String> completed = completedActionsMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());

        if (action != null && !action.isBlank()) {
            completed.add(action.toUpperCase().trim());
        }

        boolean allCompleted = completed.containsAll(REQUIRED_ACTIONS);
        log.info("Vérification de l'action [{}] pour key [{}]: {}/{} actions complétées (toutes complétées = {})",
                action, key, completed.size(), REQUIRED_ACTIONS.size(), allCompleted);

        return Mono.just(new ChallengeVerifyResponse(
                UUID.randomUUID().toString(),
                action,
                true,
                REQUIRED_ACTIONS,
                new ArrayList<>(completed),
                allCompleted
        ));
    }

    @Override
    public Mono<ChallengeStatusResponse> status(String sessionToken) {
        String key = (sessionToken != null && !sessionToken.isBlank()) ? sessionToken : "DEFAULT";
        Set<String> completed = completedActionsMap.getOrDefault(key, Set.of());
        boolean allCompleted = completed.containsAll(REQUIRED_ACTIONS);

        return Mono.just(new ChallengeStatusResponse(
                UUID.randomUUID().toString(),
                REQUIRED_ACTIONS,
                new ArrayList<>(completed),
                List.of(),
                allCompleted,
                true
        ));
    }
}

