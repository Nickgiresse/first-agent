package com.firstagent.backend.domain.exception;

public abstract class DomainException extends RuntimeException {
    private final String ruleCode;

    protected DomainException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }
}
