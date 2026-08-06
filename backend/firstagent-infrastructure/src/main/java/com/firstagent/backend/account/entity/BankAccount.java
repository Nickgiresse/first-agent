package com.firstagent.backend.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "bank_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BankAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "account_number", length = 34, nullable = false, unique = true)
  private String accountNumber;

  @Column(name = "owner_full_name", length = 150, nullable = false)
  private String ownerFullName;

  @Column(name = "first_name", length = 100, nullable = false)
  private String firstName;

  @Column(name = "last_name", length = 100, nullable = false)
  private String lastName;

  /**
   * Numéro de téléphone déclaré sur le compte au référentiel bancaire.
   *
   * <p>Sert au contrôle d'appartenance : seul ce numéro peut ouvrir l'accès au service sur ce
   * compte. Nul lorsque le référentiel ne l'a pas fourni, auquel cas le contrôle ne peut pas
   * conclure et le dossier part en revue plutôt que d'être accepté par défaut.
   */
  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "is_eligible", nullable = false)
  private boolean eligible;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
