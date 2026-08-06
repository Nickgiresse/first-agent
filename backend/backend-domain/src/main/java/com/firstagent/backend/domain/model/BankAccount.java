package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.BankAccountId;
import com.firstagent.backend.domain.model.valueobject.BankAccountNumber;
import java.time.LocalDateTime;
import java.util.Objects;

public final class BankAccount {
    private final BankAccountId id;
    private final BankAccountNumber accountNumber;
    private final String ownerFullName;
    private final String firstName;
    private final String lastName;
    private final boolean eligible;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private BankAccount(
            BankAccountId id,
            BankAccountNumber accountNumber,
            String ownerFullName,
            String firstName,
            String lastName,
            boolean eligible,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.accountNumber = Objects.requireNonNull(accountNumber);
        this.ownerFullName = Objects.requireNonNull(ownerFullName);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);
        this.eligible = eligible;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static BankAccount creer(
            BankAccountNumber accountNumber,
            String ownerFullName,
            String firstName,
            String lastName,
            boolean eligible) {
        LocalDateTime now = LocalDateTime.now();
        return new BankAccount(
                BankAccountId.generate(),
                accountNumber,
                ownerFullName,
                firstName,
                lastName,
                eligible,
                now,
                now
        );
    }

    public static BankAccount reconstituer(
            BankAccountId id,
            BankAccountNumber accountNumber,
            String ownerFullName,
            String firstName,
            String lastName,
            boolean eligible,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new BankAccount(
                id,
                accountNumber,
                ownerFullName,
                firstName,
                lastName,
                eligible,
                createdAt,
                updatedAt
        );
    }

    public BankAccountId getId() {
        return id;
    }

    public BankAccountNumber getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerFullName() {
        return ownerFullName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isEligible() {
        return eligible;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
