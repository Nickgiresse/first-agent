package com.firstagent.backend.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerificationResponse {

    private boolean eligible;
    private String firstName;
    private String lastName;
    private String sessionToken;
    private long expiresInSeconds;
}
