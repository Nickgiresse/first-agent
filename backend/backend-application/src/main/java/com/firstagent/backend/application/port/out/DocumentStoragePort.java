package com.firstagent.backend.application.port.out;

import com.firstagent.backend.domain.model.valueobject.DocumentType;
import reactor.core.publisher.Mono;

public interface DocumentStoragePort {
    Mono<Void> storeDocument(String sessionToken, DocumentType documentType, byte[] content, String fileName);
    Mono<byte[]> getDocument(String sessionToken, DocumentType documentType);
}
