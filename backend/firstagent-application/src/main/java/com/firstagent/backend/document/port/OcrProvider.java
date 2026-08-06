package com.firstagent.backend.document.port;

import com.firstagent.backend.document.model.OcrExtractionResult;

public interface OcrProvider {

  OcrExtractionResult extractIdentityDocument(byte[] frontImage, byte[] backImage);

  String getProviderName();
}
