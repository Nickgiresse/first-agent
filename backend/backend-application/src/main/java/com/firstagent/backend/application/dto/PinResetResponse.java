package com.firstagent.backend.application.dto;

public record PinResetResponse(
    boolean emailSent,
    boolean requiresBranchVisit,
    String message
) {}
