package com.firstagent.backend.onboarding.entity;

import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.common.enums.CustomerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bank_account_id", nullable = false, unique = true)
  private BankAccount bankAccount;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "onboarding_session_id", nullable = false, unique = true)
  private OnboardingSession onboardingSession;

  @Column(name = "first_name", length = 100, nullable = false)
  private String firstName;

  @Column(name = "last_name", length = 100, nullable = false)
  private String lastName;

  @Column(name = "email", length = 150, unique = true)
  private String email;

  @Column(name = "phone_number", length = 20, unique = true)
  private String phoneNumber;

  @Column(name = "pin_hash", nullable = false)
  private String pinHash;

  @Column(name = "terms_accepted", nullable = false)
  private boolean termsAccepted;

  @Column(name = "terms_accepted_at")
  private LocalDateTime termsAcceptedAt;

  @Column(name = "requires_manual_review", nullable = false)
  private boolean requiresManualReview;

  @Column(name = "manual_review_reason")
  private String manualReviewReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private CustomerStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.status == null) {
      this.status = CustomerStatus.USER;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
