package com.firstagent.backend.application.usecase;

import com.firstagent.backend.application.dto.VerifyAccountRequest;
import com.firstagent.backend.application.dto.VerifyAccountResponse;
import com.firstagent.backend.application.port.in.VerifyAccountUseCase;
import com.firstagent.backend.application.port.out.BankAccountRepositoryPort;
import com.firstagent.backend.application.port.out.OnboardingSessionRepositoryPort;
import com.firstagent.backend.application.port.out.SessionTokenGeneratorPort;
import com.firstagent.backend.application.port.out.WhatsAppBankingPort;
import com.firstagent.backend.domain.exception.BusinessRuleException;
import com.firstagent.backend.domain.model.BankAccount;
import com.firstagent.backend.domain.model.OnboardingSession;
import com.firstagent.backend.domain.model.valueobject.BankAccountNumber;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionToken;
import java.time.LocalDateTime;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class VerifyAccountUseCaseImpl implements VerifyAccountUseCase {

    private static final long SESSION_DURATION_MINUTES = 30;

    private final BankAccountRepositoryPort bankAccountRepositoryPort;
    private final OnboardingSessionRepositoryPort onboardingSessionRepositoryPort;
    private final SessionTokenGeneratorPort sessionTokenGeneratorPort;
    private final WhatsAppBankingPort whatsAppBankingPort;

    public VerifyAccountUseCaseImpl(
            BankAccountRepositoryPort bankAccountRepositoryPort,
            OnboardingSessionRepositoryPort onboardingSessionRepositoryPort,
            SessionTokenGeneratorPort sessionTokenGeneratorPort,
            WhatsAppBankingPort whatsAppBankingPort) {
        this.bankAccountRepositoryPort = Objects.requireNonNull(bankAccountRepositoryPort);
        this.onboardingSessionRepositoryPort = Objects.requireNonNull(onboardingSessionRepositoryPort);
        this.sessionTokenGeneratorPort = Objects.requireNonNull(sessionTokenGeneratorPort);
        this.whatsAppBankingPort = Objects.requireNonNull(whatsAppBankingPort);
    }

    @Override
    public Mono<VerifyAccountResponse> verifyAccount(VerifyAccountRequest request) {
        String input = request.accountSuffix() != null ? request.accountSuffix().trim() : "";
        String fullAccountNumber = input.startsWith("10005") || input.startsWith("CM") ? input : "10005" + input;
        BankAccountNumber accountNumber = new BankAccountNumber(fullAccountNumber);
        BankAccountNumber rawInputNumber = new BankAccountNumber(input);

        return whatsAppBankingPort.readAccount(accountNumber)
                .flatMap(bankAccountRepositoryPort::save)
                .onErrorResume(e -> bankAccountRepositoryPort.findByAccountNumber(accountNumber))
                .switchIfEmpty(bankAccountRepositoryPort.findByAccountNumber(accountNumber))
                .switchIfEmpty(bankAccountRepositoryPort.findByAccountNumber(rawInputNumber))
                .switchIfEmpty(Mono.error(new BusinessRuleException("RG-ACC-001", "Aucun compte bancaire trouvé avec ce numéro")))
                .flatMap(bankAccount -> {
                    if (!bankAccount.isEligible()) {
                        return Mono.error(new BusinessRuleException("RG-ACC-002", "Ce compte n'est pas éligible à l'onboarding digital"));
                    }

                    OnboardingSessionToken token = sessionTokenGeneratorPort.generate();
                    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(SESSION_DURATION_MINUTES);
                    OnboardingSession session = OnboardingSession.creer(token, bankAccount.getId(), expiresAt);

                    return onboardingSessionRepositoryPort.save(session)
                            .map(savedSession -> new VerifyAccountResponse(
                                    true,
                                    bankAccount.getFirstName(),
                                    bankAccount.getLastName(),
                                    token.value(),
                                    SESSION_DURATION_MINUTES * 60
                            ));
                });
    }
}
