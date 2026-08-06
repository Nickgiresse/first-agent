package com.firstagent.backend.pin.controller;

import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.pin.dto.PinResetConfirmRequest;
import com.firstagent.backend.pin.dto.PinResetRequest;
import com.firstagent.backend.pin.dto.PinResetResponse;
import com.firstagent.backend.pin.service.PinResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pin")
@RequiredArgsConstructor
public class PinController {

    private final PinResetService pinResetService;

    @PostMapping("/reset/request")
    public ResponseEntity<ApiResponse<PinResetResponse>> requestReset(
        @Valid @RequestBody PinResetRequest request
    ) {
        PinResetResponse response = pinResetService.requestReset(request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    @PostMapping("/reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmReset(
        @Valid @RequestBody PinResetConfirmRequest request
    ) {
        pinResetService.confirmReset(request);
        return ResponseEntity.ok(ApiResponse.success(null, "PIN réinitialisé avec succès"));
    }
}