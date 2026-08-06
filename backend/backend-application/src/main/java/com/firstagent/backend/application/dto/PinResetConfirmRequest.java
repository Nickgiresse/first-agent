package com.firstagent.backend.application.dto;

public record PinResetConfirmRequest(
    String resetToken,
    String newPin,
    String newPinConfirmation
) {}
