package com.firstagent.backend.document.service;

import com.firstagent.backend.document.dto.OcrConfirmationRequest;
import com.firstagent.backend.document.dto.OcrExtractionResponse;

public interface OcrService {

  OcrExtractionResponse extractDocumentData(String sessionToken);

  OcrExtractionResponse getExtractedData(String sessionToken);

  OcrExtractionResponse confirmExtractedData(String sessionToken, OcrConfirmationRequest request);

  boolean isConfirmed(String sessionToken);
}
