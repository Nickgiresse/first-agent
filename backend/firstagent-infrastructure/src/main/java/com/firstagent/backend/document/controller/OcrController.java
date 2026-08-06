package com.firstagent.backend.document.controller;

import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.document.dto.OcrConfirmationRequest;
import com.firstagent.backend.document.dto.OcrExtractionResponse;
import com.firstagent.backend.document.service.OcrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class OcrController {

  private final OcrService ocrService;

  @PostMapping("/ocr/extract")
  public ResponseEntity<ApiResponse<OcrExtractionResponse>> extract(
      @RequestHeader("X-Session-Token") String sessionToken) {
    OcrExtractionResponse response = ocrService.extractDocumentData(sessionToken);
    return ResponseEntity.ok(ApiResponse.success(response, "Extraction OCR réalisée avec succès"));
  }

  @GetMapping("/ocr")
  public ResponseEntity<ApiResponse<OcrExtractionResponse>> get(
      @RequestHeader("X-Session-Token") String sessionToken) {
    OcrExtractionResponse response = ocrService.getExtractedData(sessionToken);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PutMapping("/ocr")
  public ResponseEntity<ApiResponse<OcrExtractionResponse>> confirm(
      @RequestHeader("X-Session-Token") String sessionToken,
      @Valid @RequestBody OcrConfirmationRequest request) {
    OcrExtractionResponse response = ocrService.confirmExtractedData(sessionToken, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Données OCR confirmées avec succès"));
  }
}
