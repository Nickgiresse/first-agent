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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final long SESSION_DURATION_MILLIS = 30 * 60 * 1000L; // 30 minutes

    private final BankAccountRepository bankAccountRepository;
    private final OnboardingSessionRepository onboardingSessionRepository;
    private final SecureSessionTokenGenerator sessionTokenGenerator;

    @Override
    @Transactional
    public AccountVerificationResponse verifyAccount(AccountVerificationRequest request) {
        BankAccount bankAccount = bankAccountRepository.findByAccountNumber("10005" + request.getAccountSuffix())
            .orElseThrow(() -> new AccountNotFoundException("Aucun compte bancaire trouvé avec ce numéro"));

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
}
