package com.firstagent.backend.account.service;

import com.firstagent.backend.account.dto.AccountVerificationRequest;
import com.firstagent.backend.account.dto.AccountVerificationResponse;
import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.account.repository.BankAccountRepository;
import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.common.exception.AccountNotFoundException;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.security.SecureSessionTokenGenerator;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.OnboardingSessionRepository;
import com.firstagent.backend.whatsappbanking.client.WhatsAppBankingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final long SESSION_DURATION_MILLIS = 30 * 60 * 1000L; // 30 minutes

    private final BankAccountRepository bankAccountRepository;
    private final OnboardingSessionRepository onboardingSessionRepository;
    private final SecureSessionTokenGenerator sessionTokenGenerator;
    private final WhatsAppBankingClient whatsAppBankingClient;

    /**
     * Vérifie le compte saisi par le client.
     *
     * Le référentiel des comptes est celui du WhatsApp banking (source de vérité) : on l'interroge
     * via l'API machine-à-machine plutôt que de dupliquer l'information ici. La table locale
     * bank_accounts n'est qu'un miroir technique, nécessaire parce que la session d'onboarding
     * s'y rattache par clé étrangère ; elle est alimentée/rafraîchie à partir de la réponse de la
     * banque. Si la banque est injoignable, on se rabat sur ce miroir afin qu'une coupure du bot
     * n'interrompe pas un parcours déjà commencé.
     */
    @Override
    @Transactional
    public AccountVerificationResponse verifyAccount(AccountVerificationRequest request) {
        String accountNumber = "10005" + request.getAccountSuffix();
        BankAccount bankAccount = resolveFromWhatsAppBanking(accountNumber)
            .orElseGet(() -> bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Aucun compte bancaire trouvé avec ce numéro")));

        if (!bankAccount.isEligible()) {
            throw new BusinessException("Ce compte n'est pas éligible à l'onboarding digital", HttpStatus.FORBIDDEN);
        }

        String sessionToken = sessionTokenGenerator.generate();

        OnboardingSession session = OnboardingSession.builder()
            .sessionToken(sessionToken)
            .bankAccount(bankAccount)
            .status(OnboardingStatus.ACCOUNT_VERIFIED)
            .expiresAt(LocalDateTime.now().plusMinutes(30))
            .build();

        onboardingSessionRepository.save(session);

        return AccountVerificationResponse.builder()
            .eligible(true)
            .firstName(bankAccount.getFirstName())
            .lastName(bankAccount.getLastName())
            .sessionToken(sessionToken)
            .expiresInSeconds(SESSION_DURATION_MILLIS / 1000)
            .build();
    }

    /** Interroge la banque et met le miroir local à jour. Vide si le compte y est inconnu. */
    private java.util.Optional<BankAccount> resolveFromWhatsAppBanking(String accountNumber) {
        Map<String, Object> remote;
        try {
            remote = whatsAppBankingClient.readAccount(null, accountNumber);
        } catch (RuntimeException e) {
            log.warn("Référentiel bancaire injoignable pour {} : {}. Repli sur le miroir local.",
                accountNumber, e.getMessage());
            return java.util.Optional.empty();
        }
        if (remote == null || !Boolean.TRUE.equals(remote.get("exists"))) {
            return java.util.Optional.empty();
        }

        String fullName = String.valueOf(remote.getOrDefault("name", "")).trim();
        // Convention locale : le nom de famille précède les prénoms (« DZANGUE DANIEL LANDRY »).
        String lastName = fullName.isEmpty() ? "" : fullName.split("\\s+")[0];
        String firstName = fullName.contains(" ") ? fullName.substring(lastName.length()).trim() : fullName;

        BankAccount mirror = bankAccountRepository.findByAccountNumber(accountNumber)
            .orElseGet(() -> BankAccount.builder().accountNumber(accountNumber).build());
        mirror.setOwnerFullName(fullName.isEmpty() ? accountNumber : fullName);
        mirror.setFirstName(firstName.isEmpty() ? "?" : firstName);
        mirror.setLastName(lastName.isEmpty() ? "?" : lastName);
        // Un compte déjà suspendu côté banque (revue KYC, décès, inactivité) n'est pas éligible.
        Object active = remote.get("is_active");
        mirror.setEligible(active == null || !"0".equals(String.valueOf(active)));
        return java.util.Optional.of(bankAccountRepository.save(mirror));
    }
}
