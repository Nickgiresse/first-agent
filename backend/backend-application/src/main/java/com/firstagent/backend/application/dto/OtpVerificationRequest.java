package com.firstagent.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record OtpVerificationRequest(
    String code,
    String otpCode
) {
    @JsonIgnore
    public String getEffectiveCode() {
        return code != null ? code : otpCode;
    }
}
