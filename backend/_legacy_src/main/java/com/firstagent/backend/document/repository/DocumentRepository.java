package com.firstagent.backend.document.repository;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.document.entity.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<CustomerDocument, UUID> {

    List<CustomerDocument> findByCustomer_Id(UUID customerId);

    Optional<CustomerDocument> findByCustomer_IdAndDocumentType(UUID customerId, DocumentType documentType);

    boolean existsByCustomer_IdAndDocumentType(UUID customerId, DocumentType documentType);
}