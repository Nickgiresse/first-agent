package com.firstagent.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

public record ProfileCreationRequest(
    Object pin
) {
    @JsonIgnore
    public String getEffectivePin() {
        if (pin instanceof String s) {
            return s;
        } else if (pin instanceof Map<?, ?> map) {
            Object p = map.get("pin");
            return p != null ? p.toString() : null;
        }
        return null;
    }
}
