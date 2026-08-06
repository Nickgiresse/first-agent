package com.firstagent.backend.liveness.repository;

import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.liveness.entity.LivenessResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivenessResultRepository extends JpaRepository<LivenessResult, UUID> {

  Optional<LivenessResult> findByCustomer_Id(UUID customerId);

  boolean existsByCustomer_IdAndStatus(UUID customerId, LivenessStatus status);
}
