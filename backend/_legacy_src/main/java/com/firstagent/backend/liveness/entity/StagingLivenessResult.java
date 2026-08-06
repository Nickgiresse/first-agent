package com.firstagent.backend.liveness.entity;

import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staging_liveness_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class StagingLivenessResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboarding_session_id", nullable = false, unique = true)
    private OnboardingSession onboardingSession;

    @Column(name = "session_id", length = 64, nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private LivenessStatus status;

    @Column(name = "completed_actions")
    private String completedActions;

    @Column(name = "total_actions")
    private Integer totalActions;

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
            this.status = LivenessStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
