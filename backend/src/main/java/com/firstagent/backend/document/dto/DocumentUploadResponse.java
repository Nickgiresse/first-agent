package com.firstagent.backend.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {

    private UUID documentId;
    private String documentType;
    private String fileName;
    private LocalDateTime uploadedAt;
}