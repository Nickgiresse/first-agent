package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.CustomerId;
import com.firstagent.backend.domain.model.valueobject.OcrResultId;
import com.firstagent.backend.domain.model.valueobject.OcrStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public final class DocumentOcrResult {
    private final OcrResultId id;
    private final CustomerId customerId;
    private final String documentKind;
    private final String firstName;
    private final String lastName;
    private final String documentNumber;
    private final String sex;
    private final LocalDate birthDate;
    private final LocalDate expiryDate;
    private final String birthPlace;
    private final String fatherName;
    private final String motherName;
    private final String kitNumber;
    private final String requestIdentifier;
    private final String ocrProvider;
    private final Double confidenceScore;
    private final Double documentQualityScore;
    private final String paymentAmount;
    private final LocalDate paymentDate;
    private final OcrStatus status;
    private final LocalDateTime extractedAt;
    private final LocalDateTime confirmedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private DocumentOcrResult(
            OcrResultId id,
            CustomerId customerId,
            String documentKind,
            String firstName,
            String lastName,
            String documentNumber,
            String sex,
            LocalDate birthDate,
            LocalDate expiryDate,
            String birthPlace,
            String fatherName,
            String motherName,
            String kitNumber,
            String requestIdentifier,
            String ocrProvider,
            Double confidenceScore,
            Double documentQualityScore,
            String paymentAmount,
            LocalDate paymentDate,
            OcrStatus status,
            LocalDateTime extractedAt,
            LocalDateTime confirmedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.documentKind = documentKind;
        this.firstName = firstName;
        this.lastName = lastName;
        this.documentNumber = documentNumber;
        this.sex = sex;
        this.birthDate = birthDate;
        this.expiryDate = expiryDate;
        this.birthPlace = birthPlace;
        this.fatherName = fatherName;
        this.motherName = motherName;
        this.kitNumber = kitNumber;
        this.requestIdentifier = requestIdentifier;
        this.ocrProvider = Objects.requireNonNull(ocrProvider);
        this.confidenceScore = confidenceScore;
        this.documentQualityScore = documentQualityScore;
        this.paymentAmount = paymentAmount;
        this.paymentDate = paymentDate;
        this.status = Objects.requireNonNull(status);
        this.extractedAt = extractedAt;
        this.confirmedAt = confirmedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static DocumentOcrResult creer(
            CustomerId customerId,
            String documentKind,
            String firstName,
            String lastName,
            String documentNumber,
            String sex,
            LocalDate birthDate,
            LocalDate expiryDate,
            String birthPlace,
            String fatherName,
            String motherName,
            String kitNumber,
            String requestIdentifier,
            String ocrProvider,
            Double confidenceScore,
            Double documentQualityScore,
            String paymentAmount,
            LocalDate paymentDate) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentOcrResult(
                OcrResultId.generate(),
                customerId,
                documentKind,
                firstName,
                lastName,
                documentNumber,
                sex,
                birthDate,
                expiryDate,
                birthPlace,
                fatherName,
                motherName,
                kitNumber,
                requestIdentifier,
                ocrProvider,
                confidenceScore,
                documentQualityScore,
                paymentAmount,
                paymentDate,
                OcrStatus.PENDING,
                now,
                null,
                now,
                now
        );
    }

    public static DocumentOcrResult reconstituer(
            OcrResultId id,
            CustomerId customerId,
            String documentKind,
            String firstName,
            String lastName,
            String documentNumber,
            String sex,
            LocalDate birthDate,
            LocalDate expiryDate,
            String birthPlace,
            String fatherName,
            String motherName,
            String kitNumber,
            String requestIdentifier,
            String ocrProvider,
            Double confidenceScore,
            Double documentQualityScore,
            String paymentAmount,
            LocalDate paymentDate,
            OcrStatus status,
            LocalDateTime extractedAt,
            LocalDateTime confirmedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new DocumentOcrResult(
                id,
                customerId,
                documentKind,
                firstName,
                lastName,
                documentNumber,
                sex,
                birthDate,
                expiryDate,
                birthPlace,
                fatherName,
                motherName,
                kitNumber,
                requestIdentifier,
                ocrProvider,
                confidenceScore,
                documentQualityScore,
                paymentAmount,
                paymentDate,
                status,
                extractedAt,
                confirmedAt,
                createdAt,
                updatedAt
        );
    }

    public OcrResultId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public String getDocumentKind() {
        return documentKind;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getSex() {
        return sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public String getFatherName() {
        return fatherName;
    }

    public String getMotherName() {
        return motherName;
    }

    public String getKitNumber() {
        return kitNumber;
    }

    public String getRequestIdentifier() {
        return requestIdentifier;
    }

    public String getOcrProvider() {
        return ocrProvider;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public Double getDocumentQualityScore() {
        return documentQualityScore;
    }

    public String getPaymentAmount() {
        return paymentAmount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public OcrStatus getStatus() {
        return status;
    }

    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
