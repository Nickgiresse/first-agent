package com.firstagent.backend.onboarding.entity;

import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.common.enums.OnboardingStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "onboarding_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class OnboardingSession {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "session_token", nullable = false, unique = true)
  private String sessionToken;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bank_account_id", nullable = false)
  private BankAccount bankAccount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 30, nullable = false)
  private OnboardingStatus status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  /**
   * Numéro WhatsApp du client qui réalise ce parcours.
   *
   * <p>Renseigné par le bot à l'ouverture de la session : il connaît déjà son interlocuteur. Le
   * numéro n'est ni saisi ni modifiable depuis le navigateur, sans quoi n'importe qui pourrait
   * s'inscrire au nom d'un autre. Nul quand le parcours est ouvert directement depuis le web.
   */
  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "email", length = 150)
  private String email;

  @Column(name = "pending_email", length = 150)
  private String pendingEmail;

  @Column(name = "email_otp_code_hash")
  private String emailOtpCodeHash;

  @Column(name = "email_otp_expires_at")
  private LocalDateTime emailOtpExpiresAt;

  @Column(name = "email_otp_attempts", nullable = false)
  @Builder.Default
  private int emailOtpAttempts = 0;

  @Column(name = "email_otp_last_sent_at")
  private LocalDateTime emailOtpLastSentAt;

  @Column(name = "pin_hash")
  private String pinHash;

  @Column(name = "terms_accepted", nullable = false)
  private boolean termsAccepted;

  @Column(name = "terms_accepted_at")
  private LocalDateTime termsAcceptedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
