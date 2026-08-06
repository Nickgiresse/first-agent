package com.firstagent.backend.vision.dto;

import java.time.LocalDate;

public record ExtractedDocumentFields(
    String documentKind,
    String lastName,
    String firstName,
    String documentNumber,
    String sex,
    LocalDate birthDate,
    LocalDate expiryDate,
    String birthPlace,
    String fatherName,
    String motherName,
    String profession,
    String kitNumber,
    String requestIdentifier,
    String paymentAmount,
    LocalDate paymentDate) {}
