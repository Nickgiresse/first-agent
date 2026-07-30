package com.firstagent.backend.document.controller;

import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.document.dto.FaceVerificationResponse;
import com.firstagent.backend.document.service.FaceVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class FaceVerificationController {

    private final FaceVerificationService faceVerificationService;

    @PostMapping("/face-verification/verify")
    public ResponseEntity<ApiResponse<FaceVerificationResponse>> verify(@RequestHeader("X-Session-Token") String sessionToken) {
        FaceVerificationResponse response = faceVerificationService.verifyFace(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(response, "Vérification faciale réussie"));
    }

    @GetMapping("/face-verification")
    public ResponseEntity<ApiResponse<FaceVerificationResponse>> get(@RequestHeader("X-Session-Token") String sessionToken) {
        FaceVerificationResponse response = faceVerificationService.getVerification(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
