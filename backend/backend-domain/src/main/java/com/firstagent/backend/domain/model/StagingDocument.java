package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.DocumentId;
import com.firstagent.backend.domain.model.valueobject.DocumentType;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionId;
import java.time.LocalDateTime;
import java.util.Objects;

public final class StagingDocument {
    private final DocumentId id;
    private final OnboardingSessionId onboardingSessionId;
    private final DocumentType documentType;
    private final String filePath;
    private final String fileName;
    private final String mimeType;
    private final Long fileSize;
    private final LocalDateTime uploadedAt;

    private StagingDocument(
            DocumentId id,
            OnboardingSessionId onboardingSessionId,
            DocumentType documentType,
            String filePath,
            String fileName,
            String mimeType,
            Long fileSize,
            LocalDateTime uploadedAt) {
        this.id = Objects.requireNonNull(id);
        this.onboardingSessionId = Objects.requireNonNull(onboardingSessionId);
        this.documentType = Objects.requireNonNull(documentType);
        this.filePath = Objects.requireNonNull(filePath);
        this.fileName = Objects.requireNonNull(fileName);
        this.mimeType = Objects.requireNonNull(mimeType);
        this.fileSize = Objects.requireNonNull(fileSize);
        this.uploadedAt = Objects.requireNonNull(uploadedAt);
    }

    public static StagingDocument creer(
            OnboardingSessionId onboardingSessionId,
            DocumentType documentType,
            String filePath,
            String fileName,
            String mimeType,
            Long fileSize) {
        return new StagingDocument(
                DocumentId.generate(),
                onboardingSessionId,
                documentType,
                filePath,
                fileName,
                mimeType,
                fileSize,
                LocalDateTime.now()
        );
    }

    public static StagingDocument reconstituer(
            DocumentId id,
            OnboardingSessionId onboardingSessionId,
            DocumentType documentType,
            String filePath,
            String fileName,
            String mimeType,
            Long fileSize,
            LocalDateTime uploadedAt) {
        return new StagingDocument(
                id,
                onboardingSessionId,
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

    public OnboardingSessionId getOnboardingSessionId() {
        return onboardingSessionId;
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
