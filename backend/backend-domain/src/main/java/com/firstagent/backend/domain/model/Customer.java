package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.BankAccountId;
import com.firstagent.backend.domain.model.valueobject.CustomerId;
import com.firstagent.backend.domain.model.valueobject.CustomerStatus;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionId;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Customer {
    private final CustomerId id;
    private final BankAccountId bankAccountId;
    private final OnboardingSessionId onboardingSessionId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phoneNumber;
    private final String pinHash;
    private final boolean termsAccepted;
    private final LocalDateTime termsAcceptedAt;
    private final boolean requiresManualReview;
    private final String manualReviewReason;
    private final CustomerStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Customer(
            CustomerId id,
            BankAccountId bankAccountId,
            OnboardingSessionId onboardingSessionId,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String pinHash,
            boolean termsAccepted,
            LocalDateTime termsAcceptedAt,
            boolean requiresManualReview,
            String manualReviewReason,
            CustomerStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.bankAccountId = Objects.requireNonNull(bankAccountId);
        this.onboardingSessionId = Objects.requireNonNull(onboardingSessionId);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.pinHash = Objects.requireNonNull(pinHash);
        this.termsAccepted = termsAccepted;
        this.termsAcceptedAt = termsAcceptedAt;
        this.requiresManualReview = requiresManualReview;
        this.manualReviewReason = manualReviewReason;
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Customer creer(
            BankAccountId bankAccountId,
            OnboardingSessionId onboardingSessionId,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String pinHash,
            boolean termsAccepted,
            LocalDateTime termsAcceptedAt,
            boolean requiresManualReview,
            String manualReviewReason) {
        LocalDateTime now = LocalDateTime.now();
        return new Customer(
                CustomerId.generate(),
                bankAccountId,
                onboardingSessionId,
                firstName,
                lastName,
                email,
                phoneNumber,
                pinHash,
                termsAccepted,
                termsAcceptedAt,
                requiresManualReview,
                manualReviewReason,
                CustomerStatus.USER,
                now,
                now
        );
    }

    public static Customer reconstituer(
            CustomerId id,
            BankAccountId bankAccountId,
            OnboardingSessionId onboardingSessionId,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String pinHash,
            boolean termsAccepted,
            LocalDateTime termsAcceptedAt,
            boolean requiresManualReview,
            String manualReviewReason,
            CustomerStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new Customer(
                id,
                bankAccountId,
                onboardingSessionId,
                firstName,
                lastName,
                email,
                phoneNumber,
                pinHash,
                termsAccepted,
                termsAcceptedAt,
                requiresManualReview,
                manualReviewReason,
                status,
                createdAt,
                updatedAt
        );
    }

    public CustomerId getId() {
        return id;
    }

    public BankAccountId getBankAccountId() {
        return bankAccountId;
    }

    public OnboardingSessionId getOnboardingSessionId() {
        return onboardingSessionId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
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

    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }

    public String getManualReviewReason() {
        return manualReviewReason;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
