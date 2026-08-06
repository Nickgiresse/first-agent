package com.firstagent.backend.common.exception;

import com.firstagent.backend.common.dto.ErrorResponse;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Nature métier vers statut HTTP.
   *
   * <p>C'est ici, et nulle part ailleurs, que le refus métier devient un code de transport. Le
   * domaine ignore volontairement cette correspondance : ses exceptions portent un {@link
   * TypeErreurMetier}, ce qui lui permet de rester sans dépendance à Spring (charte §3). La table
   * suit celle de la charte §6.
   *
   * <p>REGLE_METIER donne 422 et non 400 : la requête est bien formée, c'est la règle de gestion
   * qui refuse. La distinction compte pour l'appelant, qui doit corriger sa donnée dans un cas et
   * renoncer dans l'autre.
   */
  private static final Map<TypeErreurMetier, HttpStatus> STATUTS =
      Map.of(
          TypeErreurMetier.VALIDATION, HttpStatus.BAD_REQUEST,
          TypeErreurMetier.REGLE_METIER, HttpStatus.UNPROCESSABLE_ENTITY,
          TypeErreurMetier.INTROUVABLE, HttpStatus.NOT_FOUND,
          TypeErreurMetier.CONFLIT, HttpStatus.CONFLICT,
          TypeErreurMetier.NON_AUTORISE, HttpStatus.UNAUTHORIZED,
          TypeErreurMetier.INTERDIT, HttpStatus.FORBIDDEN,
          TypeErreurMetier.EXPIRE, HttpStatus.GONE,
          TypeErreurMetier.SYSTEME, HttpStatus.INTERNAL_SERVER_ERROR);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
    HttpStatus status = STATUTS.getOrDefault(ex.getType(), HttpStatus.UNPROCESSABLE_ENTITY);

    // Le code de règle prime sur la nature générique : « RG-KYC-014 » situe
    // le refus dans la spécification, « REGLE_METIER » ne dit rien.
    String code = ex.getCodeRegle() != null ? ex.getCodeRegle() : ex.getType().name();

    ErrorResponse error =
        ErrorResponse.builder().success(false).errorCode(code).message(ex.getMessage()).build();
    return ResponseEntity.status(status).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).toList();

    ErrorResponse error =
        ErrorResponse.builder()
            .success(false)
            .errorCode("VALIDATION_ERROR")
            .message("Certaines données saisies sont invalides")
            .details(details)
            .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
    ErrorResponse error =
        ErrorResponse.builder()
            .success(false)
            .errorCode("VALIDATION_ERROR")
            .message("Le corps de la requête doit être un JSON valide")
            .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    log.error("Erreur non gérée lors du traitement de la requête", ex);

    ErrorResponse error =
        ErrorResponse.builder()
            .success(false)
            .errorCode("INTERNAL_ERROR")
            .message("Une erreur inattendue est survenue")
            .build();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
