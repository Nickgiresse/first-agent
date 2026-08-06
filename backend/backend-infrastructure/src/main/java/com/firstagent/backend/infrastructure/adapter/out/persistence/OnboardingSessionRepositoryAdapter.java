package com.firstagent.backend.infrastructure.adapter.out.persistence;

import com.firstagent.backend.application.port.out.OnboardingSessionRepositoryPort;
import com.firstagent.backend.domain.model.OnboardingSession;
import com.firstagent.backend.domain.model.valueobject.BankAccountId;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionId;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionToken;
import com.firstagent.backend.domain.model.valueobject.OnboardingStatus;
import com.firstagent.backend.infrastructure.adapter.out.persistence.entity.OnboardingSessionEntity;
import com.firstagent.backend.infrastructure.adapter.out.persistence.repository.OnboardingSessionR2dbcRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OnboardingSessionRepositoryAdapter implements OnboardingSessionRepositoryPort {

    private final OnboardingSessionR2dbcRepository repository;

    public OnboardingSessionRepositoryAdapter(OnboardingSessionR2dbcRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Mono<OnboardingSession> findBySessionToken(OnboardingSessionToken token) {
        return repository.findBySessionToken(token.value())
                .map(this::toDomain);
    }

    @Override
    public Mono<OnboardingSession> save(OnboardingSession session) {
        return repository.save(toEntity(session))
                .map(this::toDomain);
    }

    private OnboardingSession toDomain(OnboardingSessionEntity entity) {
        return OnboardingSession.reconstituer(
                new OnboardingSessionId(entity.getId()),
                new OnboardingSessionToken(entity.getSessionToken()),
                new BankAccountId(entity.getBankAccountId()),
                OnboardingStatus.valueOf(entity.getStatus()),
                entity.getExpiresAt(),
                entity.getEmail(),
                entity.getPendingEmail(),
                entity.getEmailOtpCodeHash(),
                entity.getEmailOtpExpiresAt(),
                entity.getEmailOtpAttempts(),
                entity.getEmailOtpLastSentAt(),
                entity.getPinHash(),
                entity.isTermsAccepted(),
                entity.getTermsAcceptedAt(),
                entity.getCreatedAt()
        );
    }

    private OnboardingSessionEntity toEntity(OnboardingSession domain) {
        OnboardingSessionEntity entity = new OnboardingSessionEntity();
        entity.setId(domain.getId().value());
        entity.setSessionToken(domain.getSessionToken().value());
        entity.setBankAccountId(domain.getBankAccountId().value());
        entity.setStatus(domain.getStatus().name());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setEmail(domain.getEmail());
        entity.setPendingEmail(domain.getPendingEmail());
        entity.setEmailOtpCodeHash(domain.getEmailOtpCodeHash());
        entity.setEmailOtpExpiresAt(domain.getEmailOtpExpiresAt());
        entity.setEmailOtpAttempts(domain.getEmailOtpAttempts());
        entity.setEmailOtpLastSentAt(domain.getEmailOtpLastSentAt());
        entity.setPinHash(domain.getPinHash());
        entity.setTermsAccepted(domain.isTermsAccepted());
        entity.setTermsAcceptedAt(domain.getTermsAcceptedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
