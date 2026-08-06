package com.firstagent.backend.audit.entity;

import com.firstagent.backend.audit.model.EntreeAudit;
import com.firstagent.backend.audit.model.TypeActeur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entrée persistée du journal forensique.
 *
 * <p>Volontairement sans {@code @Setter} et sans {@code @PreUpdate} : une entrée d'audit s'écrit
 * une fois. La seule évolution admise après insertion est la pose de l'empreinte, qui ne peut être
 * calculée qu'une fois l'identifiant et l'horodatage attribués. C'est ce que fait {@link
 * #sceller(String)}, et rien d'autre n'ouvre l'entrée à la modification.
 *
 * <p>Cette retenue côté Java ne suffirait pas seule : un UPDATE direct en base la contournerait. Un
 * garde-fou en base double donc la règle, posé par la migration V18.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AuditLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * Instant de l'événement, en UTC.
   *
   * <p>{@link Instant} plutôt que {@code LocalDateTime} : le fuseau n'est pas une décoration ici.
   * Un journal opposable doit dire à quel moment absolu l'opération a eu lieu, sans dépendre du
   * réglage du serveur qui l'a écrit.
   */
  @Column(name = "timestamp", nullable = false, updatable = false)
  private Instant timestamp;

  @Column(name = "phone", length = 32, nullable = false, updatable = false)
  private String phone;

  @Column(name = "event_type", length = 64, nullable = false, updatable = false)
  private String eventType;

  @Column(name = "status", length = 20, nullable = false, updatable = false)
  private String status;

  @Column(name = "actor", length = 128, nullable = false, updatable = false)
  private String actor;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", length = 16, nullable = false, updatable = false)
  private TypeActeur actorType;

  @Column(name = "source_ip", length = 64, nullable = false, updatable = false)
  private String sourceIp;

  @Column(name = "amount", precision = 19, scale = 2, updatable = false)
  private BigDecimal amount;

  @Column(name = "currency", length = 8, updatable = false)
  private String currency;

  @Column(name = "reference", length = 128, updatable = false)
  private String reference;

  @Column(name = "details", nullable = false, updatable = false)
  private String details;

  @Column(name = "prev_hash", length = 64, nullable = false, updatable = false)
  private String prevHash;

  /** Nullable : une entrée antérieure au scellement n'en porte pas. */
  @Column(name = "entry_hash", length = 64)
  private String entryHash;

  @PrePersist
  protected void onCreate() {
    if (timestamp == null) {
      timestamp = Instant.now();
    }
    // Tronquer dès l'écriture, à la précision que la base sait conserver.
    // Sceller une valeur à la nanoseconde puis vérifier la valeur relue à la
    // microseconde ferait paraître la chaîne rompue partout.
    timestamp = timestamp.truncatedTo(ChronoUnit.MICROS);
    if (phone == null) {
      phone = "";
    }
    if (sourceIp == null) {
      sourceIp = "";
    }
    if (details == null) {
      details = "";
    }
    if (prevHash == null) {
      prevHash = EntreeAudit.GENESE;
    }
  }

  /**
   * Pose l'empreinte de l'entrée.
   *
   * <p>Seule modification admise après insertion, et une seule fois : rescelller une entrée déjà
   * scellée effacerait précisément la preuve qu'on cherche à conserver.
   */
  public void sceller(String empreinte) {
    if (this.entryHash != null) {
      throw new IllegalStateException(
          "Cette entrée du journal est déjà scellée ; la resceller effacerait la preuve.");
    }
    this.entryHash = empreinte;
  }

  /** Vue domaine de l'entrée, telle que le scellement la couvre. */
  public EntreeAudit versDomaine() {
    return new EntreeAudit(
        id, timestamp, phone, eventType, status, actor, actorType, sourceIp, amount, currency,
        reference, details, prevHash);
  }
}
