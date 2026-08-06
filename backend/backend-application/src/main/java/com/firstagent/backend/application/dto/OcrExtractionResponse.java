package com.firstagent.backend.application.dto;

public record OcrExtractionResponse(
    String documentOcrResultId,
    String documentKind,
    String firstName,
    String lastName,
    String documentNumber,
    String sex,
    String birthDate,
    String expiryDate,
    String birthPlace,
    String fatherName,
    String motherName,
    String kitNumber,
    String requestIdentifier,
    String paymentAmount,
    String paymentDate,
    double confidenceScore,
    Double documentQualityScore,
    String status,
    String provider
) {}
