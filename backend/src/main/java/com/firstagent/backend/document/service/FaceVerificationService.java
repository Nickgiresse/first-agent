package com.firstagent.backend.document.service;

import com.firstagent.backend.document.dto.FaceVerificationResponse;

public interface FaceVerificationService {

    FaceVerificationResponse verifyFace(String sessionToken);

    FaceVerificationResponse getVerification(String sessionToken);

    boolean isVerified(String sessionToken);
}
