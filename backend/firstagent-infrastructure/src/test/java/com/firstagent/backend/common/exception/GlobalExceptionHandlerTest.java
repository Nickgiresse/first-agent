package com.firstagent.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.firstagent.backend.common.dto.ErrorResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Traduction des erreurs métier en réponses HTTP.
 *
 * <p>Cette correspondance est un contrat avec les clients de l'API : un parcours qui reçoit 422
 * sait qu'il doit renoncer, un 400 qu'il doit corriger la donnée. La confondre ne casse rien côté
 * serveur, mais fait traiter de travers chaque refus côté appelant, et personne ne s'en aperçoit
 * avant de lire des journaux.
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @ParameterizedTest(name = "{0} donne {1}")
  @CsvSource({
    "VALIDATION, 400",
    "REGLE_METIER, 422",
    "INTROUVABLE, 404",
    "CONFLIT, 409",
    "NON_AUTORISE, 401",
    "INTERDIT, 403",
    "EXPIRE, 410",
    "SYSTEME, 500",
  })
  @DisplayName("chaque nature d'erreur métier a son code HTTP")
  void chaqueType_aSonStatut(TypeErreurMetier type, int statutAttendu) {
    // Table exhaustive et non un échantillon : ajouter une valeur à
    // l'énumération sans l'inscrire dans la correspondance la ferait retomber
    // silencieusement sur 422, et le client la traiterait pour ce qu'elle n'est
    // pas.
    ResponseEntity<ErrorResponse> reponse =
        handler.handleBusinessException(new BusinessException("refus", type));

    assertThat(reponse.getStatusCode().value()).isEqualTo(statutAttendu);
  }

  @Test
  @DisplayName("le code de règle prime sur la nature générique")
  void codeDeRegle_primeSurLeType() {
    // « RG-DOC-001 » situe le refus dans la spécification et permet de le
    // retrouver ; « VALIDATION » ne dit rien à personne.
    ResponseEntity<ErrorResponse> reponse =
        handler.handleBusinessException(
            new BusinessException("chemin invalide", TypeErreurMetier.VALIDATION, "RG-DOC-001"));

    assertThat(reponse.getBody().getErrorCode()).isEqualTo("RG-DOC-001");
  }

  @Test
  @DisplayName("sans code de règle, la nature de l'erreur fait office de code")
  void sansCodeDeRegle_leTypeFaitOffice() {
    ResponseEntity<ErrorResponse> reponse =
        handler.handleBusinessException(new BusinessException("refus", TypeErreurMetier.CONFLIT));

    assertThat(reponse.getBody().getErrorCode()).isEqualTo("CONFLIT");
  }

  @Test
  @DisplayName("le message métier atteint l'appelant")
  void messageMetier_estTransmis() {
    // C'est ce message que le parcours affiche au client. Le remplacer par un
    // libellé générique priverait celui-ci de la seule indication qui lui dit
    // quoi faire.
    ResponseEntity<ErrorResponse> reponse =
        handler.handleBusinessException(
            new BusinessException("Ce compte n'est pas éligible", TypeErreurMetier.INTERDIT));

    assertThat(reponse.getBody().getMessage()).isEqualTo("Ce compte n'est pas éligible");
    assertThat(reponse.getBody().isSuccess()).isFalse();
  }

  @Test
  @DisplayName("les erreurs de validation listent chaque champ fautif")
  void validation_listeLesChampsFautifs() {
    BindingResult liaison = mock(BindingResult.class);
    when(liaison.getFieldErrors())
        .thenReturn(
            List.of(
                new FieldError("demande", "email", "Adresse électronique invalide"),
                new FieldError("demande", "pin", "Le code doit contenir 4 à 6 chiffres")));
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    when(ex.getBindingResult()).thenReturn(liaison);

    ResponseEntity<ErrorResponse> reponse = handler.handleValidationException(ex);

    // Un seul message global obligerait le client à deviner lequel des deux
    // champs corriger.
    assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(reponse.getBody().getDetails())
        .containsExactly("Adresse électronique invalide", "Le code doit contenir 4 à 6 chiffres");
  }

  @Test
  @DisplayName("un corps de requête illisible est une erreur de l'appelant, pas du serveur")
  void jsonMalforme_estUneErreurDeLAppelant() {
    ResponseEntity<ErrorResponse> reponse =
        handler.handleMalformedJson(mock(HttpMessageNotReadableException.class));

    // 400 et non 500 : rendre 500 ferait croire à une panne et déclencherait
    // des réessais inutiles, alors que la requête ne passera jamais en l'état.
    assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(reponse.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  @DisplayName("une erreur imprévue ne divulgue rien de son origine")
  void erreurImprevue_neDivulguePasSonOrigine() {
    ResponseEntity<ErrorResponse> reponse =
        handler.handleGenericException(
            new IllegalStateException(
                "Connection to jdbc:postgresql://10.0.0.5:5432/firstagent refused"));

    // Le détail technique part dans les journaux, pas dans la réponse : une
    // trace de pile ou une chaîne de connexion renseignent un attaquant sur
    // l'infrastructure.
    assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(reponse.getBody().getMessage())
        .doesNotContain("jdbc")
        .doesNotContain("10.0.0.5")
        .doesNotContain("postgresql");
  }
}
