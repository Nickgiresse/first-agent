package com.firstagent.backend.document.controller;

import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.document.dto.DocumentUploadResponse;
import com.firstagent.backend.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/{customerId}/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<DocumentUploadResponse>> uploadDocument(
        @PathVariable UUID customerId,
        @RequestParam("documentType") DocumentType documentType,
        @RequestParam("file") MultipartFile file
    ) {
        DocumentUploadResponse response = documentService.uploadDocument(customerId, documentType, file);
        return ResponseEntity.ok(ApiResponse.success(response, "Document téléversé avec succès"));
    }
}