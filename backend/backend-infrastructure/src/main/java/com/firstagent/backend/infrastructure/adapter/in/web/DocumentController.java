package com.firstagent.backend.infrastructure.adapter.in.web;

import com.firstagent.backend.application.dto.DocumentUploadResponse;
import com.firstagent.backend.application.port.in.DocumentUseCase;
import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.domain.model.valueobject.DocumentType;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentUseCase documentUseCase;

    public DocumentController(DocumentUseCase documentUseCase) {
        this.documentUseCase = Objects.requireNonNull(documentUseCase);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<DocumentUploadResponse>>> uploadDocument(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestParam("documentType") DocumentType documentType,
            @RequestPart("file") FilePart file
    ) {
        return documentUseCase.uploadDocument(sessionToken, documentType, file)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Document téléversé avec succès")));
    }
}
