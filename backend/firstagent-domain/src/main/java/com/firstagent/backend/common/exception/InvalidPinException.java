package com.firstagent.backend.common.exception;

/** Code PIN invalide. */
public class InvalidPinException extends BusinessException {

    public InvalidPinException(String message) {
        super(message, TypeErreurMetier.NON_AUTORISE);
    }

    public InvalidPinException(String message, String codeRegle) {
        super(message, TypeErreurMetier.NON_AUTORISE, codeRegle);
    }
}
