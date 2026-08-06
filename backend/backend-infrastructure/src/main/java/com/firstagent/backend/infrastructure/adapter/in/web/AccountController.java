package com.firstagent.backend.infrastructure.adapter.in.web;

import com.firstagent.backend.application.dto.VerifyAccountRequest;
import com.firstagent.backend.application.dto.VerifyAccountResponse;
import com.firstagent.backend.application.port.in.VerifyAccountUseCase;
import com.firstagent.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final VerifyAccountUseCase verifyAccountUseCase;

    public AccountController(VerifyAccountUseCase verifyAccountUseCase) {
        this.verifyAccountUseCase = Objects.requireNonNull(verifyAccountUseCase);
    }

    @PostMapping("/verify")
    public Mono<ResponseEntity<ApiResponse<VerifyAccountResponse>>> verifyAccount(
            @Valid @RequestBody VerifyAccountRequest request
    ) {
        return verifyAccountUseCase.verifyAccount(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Compte vérifié avec succès")));
    }
}
