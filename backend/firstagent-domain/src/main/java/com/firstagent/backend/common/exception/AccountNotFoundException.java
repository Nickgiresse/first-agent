package com.firstagent.backend.common.exception;

/** Compte bancaire introuvable au référentiel. */
public class AccountNotFoundException extends BusinessException {

  public AccountNotFoundException(String message) {
    super(message, TypeErreurMetier.INTROUVABLE);
  }

  public AccountNotFoundException(String message, String codeRegle) {
    super(message, TypeErreurMetier.INTROUVABLE, codeRegle);
  }
}
