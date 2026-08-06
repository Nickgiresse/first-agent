package com.firstagent.backend.document.service;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.document.dto.DocumentUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    DocumentUploadResponse uploadDocument(String sessionToken, DocumentType documentType, MultipartFile file);

    boolean hasAllRequiredDocuments(String sessionToken);
}
