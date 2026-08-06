package com.firstagent.backend.document.model;

import java.time.LocalDate;
import java.util.List;

public record OcrExtractionResult(
    String documentKind, // CNI | TITRE_PROVISOIRE | RECEPISSE | UNKNOWN
    String firstName,
    String lastName,
    String documentNumber, // CNI uniquement
    String sex, // CNI uniquement
    LocalDate birthDate,
    LocalDate expiryDate,
    String birthPlace,
    String fatherName, // titre provisoire uniquement
    String motherName, // titre provisoire uniquement
    String kitNumber, // titre provisoire et récépissé
    String requestIdentifier, // titre provisoire et récépissé
    String paymentAmount, // récépissé uniquement
    LocalDate paymentDate, // récépissé uniquement
    double confidenceScore,
    double documentQualityScore,
    List<String> issues) {}
