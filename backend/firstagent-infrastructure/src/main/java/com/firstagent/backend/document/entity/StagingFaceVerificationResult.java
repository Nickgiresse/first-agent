package com.firstagent.backend.document.entity;

import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "staging_face_verification_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class StagingFaceVerificationResult {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "onboarding_session_id", nullable = false, unique = true)
  private OnboardingSession onboardingSession;

  @Column(name = "similarity_score")
  private Double similarityScore;

  @Column(name = "target_quality_score")
  private Double targetQualityScore;

  @Column(name = "provider", length = 30, nullable = false)
  private String provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private FaceVerificationStatus status;

  @Column(name = "verified_at")
  private LocalDateTime verifiedAt;

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
      this.status = FaceVerificationStatus.PENDING;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
