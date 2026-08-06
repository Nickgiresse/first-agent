package com.firstagent.backend.application.dto;

import java.time.LocalDateTime;

public record DocumentUploadResponse(
    String documentId,
    String documentType,
    String fileName,
    LocalDateTime uploadedAt
) {}
