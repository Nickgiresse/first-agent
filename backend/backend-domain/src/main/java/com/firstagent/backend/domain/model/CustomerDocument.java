package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.CustomerId;
import com.firstagent.backend.domain.model.valueobject.DocumentId;
import com.firstagent.backend.domain.model.valueobject.DocumentType;
import java.time.LocalDateTime;
import java.util.Objects;

public final class CustomerDocument {
    private final DocumentId id;
    private final CustomerId customerId;
    private final DocumentType documentType;
    private final String filePath;
    private final String fileName;
    private final String mimeType;
    private final Long fileSize;
    private final LocalDateTime uploadedAt;

    private CustomerDocument(
            DocumentId id,
            CustomerId customerId,
            DocumentType documentType,
            String filePath,
            String fileName,
            String mimeType,
            Long fileSize,
            LocalDateTime uploadedAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.documentType = Objects.requireNonNull(documentType);
        this.filePath = Objects.requireNonNull(filePath);
        this.fileName = Objects.requireNonNull(fileName);
        this.mimeType = Objects.requireNonNull(mimeType);
        this.fileSize = Objects.requireNonNull(fileSize);
        this.uploadedAt = Objects.requireNonNull(uploadedAt);
    }

    public static CustomerDocument creer(
            CustomerId customerId,
            DocumentType documentType,
            String filePath,
            String fileName,
            String mimeType,
            Long fileSize) {
        return new CustomerDocument(
                DocumentId.generate(),
                customerId,
                documentType,
                filePath,
                fileName,
                mimeType,
                fileSize,
                LocalDateTime.now()
        );
    }

    public static CustomerDocument reconstituer(
            DocumentId id,
            CustomerId customerId,
            DocumentType documentType,
            String filePath,
            String fileName,
            String mimeType,
            Long fileSize,
            LocalDateTime uploadedAt) {
        return new CustomerDocument(
                id,
                customerId,
                documentType,
                filePath,
                fileName,
                mimeType,
                fileSize,
                uploadedAt
        );
    }

    public DocumentId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
