package com.firstagent.backend.application.usecase;

import com.firstagent.backend.application.dto.DocumentUploadResponse;
import com.firstagent.backend.application.port.in.DocumentUseCase;
import com.firstagent.backend.application.port.out.DocumentStoragePort;
import com.firstagent.backend.domain.model.valueobject.DocumentType;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public class DocumentUseCaseImpl implements DocumentUseCase {

    private final DocumentStoragePort documentStoragePort;

    public DocumentUseCaseImpl(DocumentStoragePort documentStoragePort) {
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort);
    }

    @Override
    public Mono<DocumentUploadResponse> uploadDocument(String sessionToken, DocumentType documentType, FilePart file) {
        String docId = UUID.randomUUID().toString();
        String fileName = file != null ? file.filename() : "document.png";

        if (file == null) {
            return Mono.just(new DocumentUploadResponse(docId, documentType.name(), fileName, LocalDateTime.now()));
        }

        return DataBufferUtils.join(file.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return documentStoragePort.storeDocument(sessionToken, documentType, bytes, fileName)
                            .then(Mono.just(new DocumentUploadResponse(docId, documentType.name(), fileName, LocalDateTime.now())));
                })
                .defaultIfEmpty(new DocumentUploadResponse(docId, documentType.name(), fileName, LocalDateTime.now()));
    }
}
