package com.firstagent.backend.document.service;

public interface FaceMatchProvider {

    FaceMatchResult compareFaces(byte[] referenceImage, byte[] targetImage);

    String getProviderName();
}
