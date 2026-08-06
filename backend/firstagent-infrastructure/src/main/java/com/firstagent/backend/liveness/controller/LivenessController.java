package com.firstagent.backend.liveness.controller;

import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.liveness.dto.ChallengeStartResponse;
import com.firstagent.backend.liveness.dto.ChallengeStatusResponse;
import com.firstagent.backend.liveness.dto.ChallengeVerifyResponse;
import com.firstagent.backend.liveness.service.LivenessService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents/liveness")
@RequiredArgsConstructor
public class LivenessController {

  private final LivenessService livenessService;

  @PostMapping("/challenge/start")
  public ResponseEntity<ApiResponse<ChallengeStartResponse>> start(
      @RequestHeader("X-Session-Token") String sessionToken) {
    ChallengeStartResponse response = livenessService.startChallenge(sessionToken);
    return ResponseEntity.ok(ApiResponse.success(response, "Défi de vivacité démarré"));
  }

  @PostMapping("/challenge/verify")
  public ResponseEntity<ApiResponse<ChallengeVerifyResponse>> verify(
      @RequestHeader("X-Session-Token") String sessionToken,
      @RequestParam String action,
      @RequestParam("frames") List<MultipartFile> frames) {
    List<byte[]> frameBytes = frames.stream().map(this::readBytes).toList();
    ChallengeVerifyResponse response =
        livenessService.verifyAction(sessionToken, action, frameBytes);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/challenge/status")
  public ResponseEntity<ApiResponse<ChallengeStatusResponse>> status(
      @RequestHeader("X-Session-Token") String sessionToken) {
    ChallengeStatusResponse response = livenessService.getStatus(sessionToken);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new BusinessException(
          "Impossible de lire une image de la rafale envoyée : " + e.getMessage());
    }
  }
}
