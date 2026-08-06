package com.firstagent.backend.document.repository;

import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.document.entity.FaceVerificationResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceVerificationResultRepository
    extends JpaRepository<FaceVerificationResult, UUID> {

  Optional<FaceVerificationResult> findByCustomer_Id(UUID customerId);

  boolean existsByCustomer_IdAndStatus(UUID customerId, FaceVerificationStatus status);
}
