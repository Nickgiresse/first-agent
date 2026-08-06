package com.firstagent.backend.application.dto;

public record OcrConfirmationRequest(
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
    String paymentDate
) {}
