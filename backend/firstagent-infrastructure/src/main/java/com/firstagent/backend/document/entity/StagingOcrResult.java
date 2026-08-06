package com.firstagent.backend.document.entity;

import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "staging_ocr_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class StagingOcrResult {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "onboarding_session_id", nullable = false, unique = true)
  private OnboardingSession onboardingSession;

  @Column(name = "document_kind", length = 20)
  private String documentKind;

  @Column(name = "first_name", length = 100)
  private String firstName;

  @Column(name = "last_name", length = 100)
  private String lastName;

  @Column(name = "document_number", length = 50)
  private String documentNumber;

  @Column(name = "sex", length = 5)
  private String sex;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  @Column(name = "birth_place", length = 150)
  private String birthPlace;

  @Column(name = "father_name", length = 150)
  private String fatherName;

  @Column(name = "mother_name", length = 150)
  private String motherName;

  @Column(name = "kit_number", length = 30)
  private String kitNumber;

  @Column(name = "request_identifier", length = 50)
  private String requestIdentifier;

  @Column(name = "ocr_provider", length = 30, nullable = false)
  private String ocrProvider;

  @Column(name = "confidence_score")
  private Double confidenceScore;

  @Column(name = "document_quality_score")
  private Double documentQualityScore;

  @Column(name = "payment_amount", length = 20)
  private String paymentAmount;

  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private OcrStatus status;

  @Column(name = "extracted_at")
  private LocalDateTime extractedAt;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

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
      this.status = OcrStatus.PENDING;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
