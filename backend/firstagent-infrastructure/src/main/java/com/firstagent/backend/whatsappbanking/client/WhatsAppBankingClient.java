package com.firstagent.backend.whatsappbanking.client;

import com.firstagent.backend.common.exception.BusinessException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client vers l'API machine-à-machine (M2M) du WhatsApp banking (dépôt firstagent-demo), qui est la
 * SOURCE DE VÉRITÉ du client final. Ce microservice ne touche jamais la base du WhatsApp banking
 * directement : il passe par cette API à clé (en-tête X-API-Key), ce qui préserve la logique
 * métier, le journal d'audit scellé et l'unicité de la source de vérité.
 *
 * <p>Contrat côté WhatsApp banking (gateway/onboarding_api.py) : POST /api/onboarding/verify-token
 * {token} -> valide sans consommer GET /api/onboarding/account?phone=&rib= -> éligibilité /
 * identité CBS POST /api/onboarding/customer {token, name, account_number, -> écrit/MAJ, consomme
 * le token pin, decision, kyc_*, cgu_accepted, source_ip}
 */
@Component
@Slf4j
public class WhatsAppBankingClient {

  private final RestTemplate restTemplate = new RestTemplate();
  private final String baseUrl;
  private final String apiKey;

  public WhatsAppBankingClient(
      @Value("${app.whatsapp-banking.base-url:http://localhost:8000}") String baseUrl,
      @Value("${app.whatsapp-banking.api-key:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException(
          "La clé API du WhatsApp banking (app.whatsapp-banking.api-key) est obligatoire. "
              + "Renseignez la même valeur que ONBOARDING_API_KEY côté firstagent-demo.");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
  }

  /**
   * Valide le JWT du lien SANS le consommer et renvoie le contexte (téléphone, langue, identité).
   */
  public Map<String, Object> verifyToken(String token) {
    Map<String, Object> body = new HashMap<>();
    body.put("token", token);
    return postJson("/api/onboarding/verify-token", body);
  }

  /** Lecture éligibilité / identité connue pour le croisement de nom (OCR ↔ CBS). */
  public Map<String, Object> readAccount(String phone, String rib) {
    // fromUriString (et non fromHttpUrl, retiré dans Spring Framework 7 / Boot 4).
    String url =
        UriComponentsBuilder.fromUriString(baseUrl + "/api/onboarding/account")
            .queryParam("phone", phone == null ? "" : phone)
            .queryParam("rib", rib == null ? "" : rib)
            .toUriString();
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.set("X-API-Key", apiKey);
      return restTemplate
          .exchange(
              url,
              HttpMethod.GET,
              new HttpEntity<>(headers),
              new ParameterizedTypeReference<Map<String, Object>>() {})
          .getBody();
    } catch (RestClientException e) {
      throw translateError(e);
    }
  }

  /**
   * Écrit ou met à jour le client onboardé dans le WhatsApp banking et CONSOMME le token du lien
   * (usage unique). {@code decision} vaut MATCH (compte actif), REVIEW (compte suspendu, revue
   * conseiller) ou NO_MATCH (refus, aucun compte créé, tentative archivée).
   */
  public Map<String, Object> upsertCustomer(
      String token,
      String name,
      String accountNumber,
      String pin,
      String decision,
      Double kycScore,
      String kycCniNumber,
      String kycDocType,
      boolean cguAccepted,
      String sourceIp) {
    Map<String, Object> body = new HashMap<>();
    body.put("token", token);
    body.put("name", name);
    body.put("account_number", accountNumber);
    body.put("pin", pin);
    body.put("decision", decision);
    body.put("kyc_score", kycScore);
    body.put("kyc_cni_number", kycCniNumber);
    body.put("kyc_doc_type", kycDocType);
    body.put("cgu_accepted", cguAccepted);
    body.put("source_ip", sourceIp == null ? "" : sourceIp);
    return postJson("/api/onboarding/customer", body);
  }

  private Map<String, Object> postJson(String path, Map<String, Object> body) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-API-Key", apiKey);
      HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
      return restTemplate
          .exchange(
              baseUrl + path,
              HttpMethod.POST,
              request,
              new ParameterizedTypeReference<Map<String, Object>>() {})
          .getBody();
    } catch (RestClientException e) {
      throw translateError(e);
    }
  }

  private BusinessException translateError(RestClientException e) {
    if (e instanceof HttpStatusCodeException httpError) {
      String detail = extractDetail(httpError.getResponseBodyAsString());
      if (detail != null) {
        log.warn(
            "Le WhatsApp banking a refusé la requête ({}) : {}", httpError.getStatusCode(), detail);
        return new BusinessException(detail);
      }
    }
    log.error("Erreur de communication avec le WhatsApp banking : {}", e.getMessage());
    return new BusinessException(
        "Erreur de communication avec le service bancaire. Vérifiez qu'il est bien démarré : "
            + e.getMessage());
  }

  /**
   * L'API M2M renvoie ses erreurs sous {"detail": {"message": "..."}} (FastAPI HTTPException).
   * Extraction sans dépendance Jackson : le backend tourne sur Jackson 3 (tools.jackson) et lier ce
   * client à une version précise le rendrait fragile aux montées de version.
   */
  private String extractDetail(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    int i = responseBody.indexOf("\"message\"");
    if (i < 0) {
      return null;
    }
    int start = responseBody.indexOf('"', responseBody.indexOf(':', i) + 1);
    int end = start < 0 ? -1 : responseBody.indexOf('"', start + 1);
    return (start < 0 || end < 0) ? null : responseBody.substring(start + 1, end);
  }
}
