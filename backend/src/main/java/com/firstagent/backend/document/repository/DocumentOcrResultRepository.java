package com.firstagent.backend.document.repository;

import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.document.entity.DocumentOcrResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentOcrResultRepository extends JpaRepository<DocumentOcrResult, UUID> {

    Optional<DocumentOcrResult> findByCustomer_Id(UUID customerId);

    boolean existsByCustomer_IdAndStatus(UUID customerId, OcrStatus status);
}
