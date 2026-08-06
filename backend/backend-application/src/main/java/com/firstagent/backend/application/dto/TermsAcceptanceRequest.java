package com.firstagent.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TermsAcceptanceRequest(
    Boolean termsAccepted,
    Boolean accepted
) {
    @JsonIgnore
    public boolean isAccepted() {
        if (termsAccepted != null) return termsAccepted;
        if (accepted != null) return accepted;
        return false;
    }
}
