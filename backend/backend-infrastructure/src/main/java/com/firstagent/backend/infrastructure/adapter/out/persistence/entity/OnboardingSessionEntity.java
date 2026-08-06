package com.firstagent.backend.infrastructure.adapter.out.persistence.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("onboarding_sessions")
public class OnboardingSessionEntity {
    @Id
    private UUID id;

    @Column("session_token")
    private String sessionToken;

    @Column("bank_account_id")
    private UUID bankAccountId;

    @Column("status")
    private String status;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("email")
    private String email;

    @Column("pending_email")
    private String pendingEmail;

    @Column("email_otp_code_hash")
    private String emailOtpCodeHash;

    @Column("email_otp_expires_at")
    private LocalDateTime emailOtpExpiresAt;

    @Column("email_otp_attempts")
    private int emailOtpAttempts;

    @Column("email_otp_last_sent_at")
    private LocalDateTime emailOtpLastSentAt;

    @Column("pin_hash")
    private String pinHash;

    @Column("terms_accepted")
    private boolean termsAccepted;

    @Column("terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public UUID getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(UUID bankAccountId) { this.bankAccountId = bankAccountId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }

    public String getEmailOtpCodeHash() { return emailOtpCodeHash; }
    public void setEmailOtpCodeHash(String emailOtpCodeHash) { this.emailOtpCodeHash = emailOtpCodeHash; }

    public LocalDateTime getEmailOtpExpiresAt() { return emailOtpExpiresAt; }
    public void setEmailOtpExpiresAt(LocalDateTime emailOtpExpiresAt) { this.emailOtpExpiresAt = emailOtpExpiresAt; }

    public int getEmailOtpAttempts() { return emailOtpAttempts; }
    public void setEmailOtpAttempts(int emailOtpAttempts) { this.emailOtpAttempts = emailOtpAttempts; }

    public LocalDateTime getEmailOtpLastSentAt() { return emailOtpLastSentAt; }
    public void setEmailOtpLastSentAt(LocalDateTime emailOtpLastSentAt) { this.emailOtpLastSentAt = emailOtpLastSentAt; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public boolean isTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }

    public LocalDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(LocalDateTime termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
