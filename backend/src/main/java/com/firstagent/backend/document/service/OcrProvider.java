package com.firstagent.backend.document.service;

public interface OcrProvider {

    OcrExtractionResult extractIdentityDocument(byte[] frontImage, byte[] backImage);

    String getProviderName();
}
