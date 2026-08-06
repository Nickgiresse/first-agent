package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.BankAccountId;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionId;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionToken;
import com.firstagent.backend.domain.model.valueobject.OnboardingStatus;
import java.time.LocalDateTime;
import java.util.Objects;

public final class OnboardingSession {
    private final OnboardingSessionId id;
    private final OnboardingSessionToken sessionToken;
    private final BankAccountId bankAccountId;
    private final OnboardingStatus status;
    private final LocalDateTime expiresAt;
    private final String email;
    private final String pendingEmail;
    private final String emailOtpCodeHash;
    private final LocalDateTime emailOtpExpiresAt;
    private final int emailOtpAttempts;
    private final LocalDateTime emailOtpLastSentAt;
    private final String pinHash;
    private final boolean termsAccepted;
    private final LocalDateTime termsAcceptedAt;
    private final LocalDateTime createdAt;

    private OnboardingSession(
            OnboardingSessionId id,
            OnboardingSessionToken sessionToken,
            BankAccountId bankAccountId,
            OnboardingStatus status,
            LocalDateTime expiresAt,
            String email,
            String pendingEmail,
            String emailOtpCodeHash,
            LocalDateTime emailOtpExpiresAt,
            int emailOtpAttempts,
            LocalDateTime emailOtpLastSentAt,
            String pinHash,
            boolean termsAccepted,
            LocalDateTime termsAcceptedAt,
            LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id);
        this.sessionToken = Objects.requireNonNull(sessionToken);
        this.bankAccountId = Objects.requireNonNull(bankAccountId);
        this.status = Objects.requireNonNull(status);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.email = email;
        this.pendingEmail = pendingEmail;
        this.emailOtpCodeHash = emailOtpCodeHash;
        this.emailOtpExpiresAt = emailOtpExpiresAt;
        this.emailOtpAttempts = emailOtpAttempts;
        this.emailOtpLastSentAt = emailOtpLastSentAt;
        this.pinHash = pinHash;
        this.termsAccepted = termsAccepted;
        this.termsAcceptedAt = termsAcceptedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static OnboardingSession creer(
            OnboardingSessionToken sessionToken,
            BankAccountId bankAccountId,
            LocalDateTime expiresAt) {
        LocalDateTime now = LocalDateTime.now();
        return new OnboardingSession(
                OnboardingSessionId.generate(),
                sessionToken,
                bankAccountId,
                OnboardingStatus.ACCOUNT_VERIFIED,
                expiresAt,
                null, null, null, null, 0, null, null, false, null,
                now
        );
    }

    public static OnboardingSession reconstituer(
            OnboardingSessionId id,
            OnboardingSessionToken sessionToken,
            BankAccountId bankAccountId,
            OnboardingStatus status,
            LocalDateTime expiresAt,
            String email,
            String pendingEmail,
            String emailOtpCodeHash,
            LocalDateTime emailOtpExpiresAt,
            int emailOtpAttempts,
            LocalDateTime emailOtpLastSentAt,
            String pinHash,
            boolean termsAccepted,
            LocalDateTime termsAcceptedAt,
            LocalDateTime createdAt) {
        return new OnboardingSession(
                id,
                sessionToken,
                bankAccountId,
                status,
                expiresAt,
                email,
                pendingEmail,
                emailOtpCodeHash,
                emailOtpExpiresAt,
                emailOtpAttempts,
                emailOtpLastSentAt,
                pinHash,
                termsAccepted,
                termsAcceptedAt,
                createdAt
        );
    }

    public OnboardingSessionId getId() {
        return id;
    }

    public OnboardingSessionToken getSessionToken() {
        return sessionToken;
    }

    public BankAccountId getBankAccountId() {
        return bankAccountId;
    }

    public OnboardingStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public String getEmail() {
        return email;
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public String getEmailOtpCodeHash() {
        return emailOtpCodeHash;
    }

    public LocalDateTime getEmailOtpExpiresAt() {
        return emailOtpExpiresAt;
    }

    public int getEmailOtpAttempts() {
        return emailOtpAttempts;
    }

    public LocalDateTime getEmailOtpLastSentAt() {
        return emailOtpLastSentAt;
    }

    public String getPinHash() {
        return pinHash;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public LocalDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
