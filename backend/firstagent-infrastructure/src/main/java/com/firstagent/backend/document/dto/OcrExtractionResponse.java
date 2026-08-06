package com.firstagent.backend.document.dto;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrExtractionResponse {

  private UUID documentOcrResultId;
  private String documentKind;
  private String firstName;
  private String lastName;
  private String documentNumber;
  private String sex;
  private LocalDate birthDate;
  private LocalDate expiryDate;
  private String birthPlace;
  private String fatherName;
  private String motherName;
  private String kitNumber;
  private String requestIdentifier;
  private String paymentAmount;
  private LocalDate paymentDate;
  private Double confidenceScore;
  private Double documentQualityScore;
  private String status;
  private String provider;
}
