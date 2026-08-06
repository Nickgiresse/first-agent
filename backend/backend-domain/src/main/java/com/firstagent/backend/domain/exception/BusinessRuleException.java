package com.firstagent.backend.domain.exception;

public class BusinessRuleException extends DomainException {
    public BusinessRuleException(String ruleCode, String message) {
        super(ruleCode, message);
    }
}
