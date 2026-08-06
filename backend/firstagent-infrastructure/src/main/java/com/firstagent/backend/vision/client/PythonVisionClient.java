package com.firstagent.backend.vision.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.vision.dto.*;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client unique vers le microservice Python de vision (OCR, visage, vivacité, comparaison
 * biométrique). Point d'entrée unique de ce backend vers ce service — Angular ne l'appelle jamais
 * directement. Remplace les anciennes intégrations Mindee/AWS Rekognition/CompreFace.
 */
@Component
@Slf4j
public class PythonVisionClient {

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String baseUrl;
  private final String apiKey;

  public PythonVisionClient(
      @Value("${app.vision-service.base-url:http://localhost:8001}") String baseUrl,
      @Value("${app.vision-service.api-key:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException(
          "La clé API du microservice de vision (app.vision-service.api-key) est obligatoire. "
              + "Démarrez python-service (voir README) puis renseignez la même valeur que son INTERNAL_API_KEY.");
    }
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  public DocumentAnalyzeResult analyzeDocument(byte[] image, String filename) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", namedResource(image, filename));
    return post("/api/v1/document/analyze", body, new ParameterizedTypeReference<>() {});
  }

  public DocumentExtractResult extractDocument(byte[] front, byte[] back) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("front", namedResource(front, "front.jpg"));
    if (back != null && back.length > 0) {
      body.add("back", namedResource(back, "back.jpg"));
    }
    return post("/api/v1/document/extract", body, new ParameterizedTypeReference<>() {});
  }

  public FaceAnalyzeResult analyzeFace(byte[] image) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", namedResource(image, "face.jpg"));
    return post("/api/v1/face/analyze", body, new ParameterizedTypeReference<>() {});
  }

  public FaceCompareResult compareFaces(byte[] source, byte[] target) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("source", namedResource(source, "source.jpg"));
    body.add("target", namedResource(target, "target.jpg"));
    return post("/api/v1/verification/compare", body, new ParameterizedTypeReference<>() {});
  }

  public LivenessChallengeStart startLivenessChallenge() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Internal-Api-Key", apiKey);
    try {
      return restTemplate
          .exchange(
              baseUrl + "/api/v1/liveness/challenge/start",
              HttpMethod.POST,
              new HttpEntity<>(headers),
              new ParameterizedTypeReference<LivenessChallengeStart>() {})
          .getBody();
    } catch (RestClientException e) {
      throw translateError(e);
    }
  }

  public LivenessChallengeVerify verifyLivenessChallenge(
      String sessionId, String action, List<byte[]> frames) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("session_id", sessionId);
    body.add("action", action);
    for (int i = 0; i < frames.size(); i++) {
      body.add("frames", namedResource(frames.get(i), "frame" + i + ".jpg"));
    }
    return post("/api/v1/liveness/challenge/verify", body, new ParameterizedTypeReference<>() {});
  }

  public LivenessChallengeStatus getLivenessStatus(String sessionId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Internal-Api-Key", apiKey);
    try {
      return restTemplate
          .exchange(
              baseUrl + "/api/v1/liveness/challenge/" + sessionId + "/status",
              HttpMethod.GET,
              new HttpEntity<>(headers),
              new ParameterizedTypeReference<LivenessChallengeStatus>() {})
          .getBody();
    } catch (RestClientException e) {
      throw translateError(e);
    }
  }

  private <T> T post(
      String path, MultiValueMap<String, Object> body, ParameterizedTypeReference<T> responseType) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      headers.set("X-Internal-Api-Key", apiKey);

      HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
      return restTemplate
          .exchange(baseUrl + path, HttpMethod.POST, request, responseType)
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
            "Le microservice de vision a refusé la requête ({}) : {}",
            httpError.getStatusCode(),
            detail);
        return new BusinessException(detail);
      }
    }
    log.error("Erreur de communication avec le microservice de vision : {}", e.getMessage());
    return new BusinessException(
        "Erreur de communication avec le service de vérification d'identité. Vérifiez qu'il est bien démarré : "
            + e.getMessage());
  }

  private String extractDetail(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      JsonNode detail = objectMapper.readTree(responseBody).get("detail");
      return detail != null && detail.isTextual() ? detail.asText() : null;
    } catch (Exception ex) {
      return null;
    }
  }

  private static ByteArrayResource namedResource(byte[] bytes, String filename) {
    return new ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }
}
