package com.firstagent.backend.application.port.in;

import com.firstagent.backend.application.dto.DocumentUploadResponse;
import com.firstagent.backend.domain.model.valueobject.DocumentType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface DocumentUseCase {
    Mono<DocumentUploadResponse> uploadDocument(String sessionToken, DocumentType documentType, FilePart file);
}
