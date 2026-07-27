package com.firstagent.backend.document.service;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.document.dto.DocumentUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentService {

    DocumentUploadResponse uploadDocument(UUID customerId, DocumentType documentType, MultipartFile file);

    boolean hasAllRequiredDocuments(UUID customerId);
}