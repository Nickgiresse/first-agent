package com.firstagent.backend.account.controller;

import com.firstagent.backend.account.dto.AccountVerificationRequest;
import com.firstagent.backend.account.dto.AccountVerificationResponse;
import com.firstagent.backend.account.service.AccountService;
import com.firstagent.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<AccountVerificationResponse>> verifyAccount(
        @Valid @RequestBody AccountVerificationRequest request
    ) {
        AccountVerificationResponse response = accountService.verifyAccount(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Compte vérifié avec succès"));
    }
}