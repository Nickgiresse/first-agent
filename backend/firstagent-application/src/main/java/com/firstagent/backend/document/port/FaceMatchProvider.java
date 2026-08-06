package com.firstagent.backend.document.port;

import com.firstagent.backend.document.model.FaceMatchResult;

public interface FaceMatchProvider {

  FaceMatchResult compareFaces(byte[] referenceImage, byte[] targetImage);

  String getProviderName();
}
