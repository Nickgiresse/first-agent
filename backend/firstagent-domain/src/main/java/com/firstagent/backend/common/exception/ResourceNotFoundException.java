package com.firstagent.backend.common.exception;

/** Ressource introuvable. */
public class ResourceNotFoundException extends BusinessException {

  public ResourceNotFoundException(String message) {
    super(message, TypeErreurMetier.INTROUVABLE);
  }

  public ResourceNotFoundException(String message, String codeRegle) {
    super(message, TypeErreurMetier.INTROUVABLE, codeRegle);
  }
}
