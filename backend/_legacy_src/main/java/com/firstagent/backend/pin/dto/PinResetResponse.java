package com.firstagent.backend.pin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinResetResponse {

    private boolean emailSent;
    private boolean requiresBranchVisit;
    private String message;
}