package com.firstagent.backend.vision.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.vision.dto.DeiOcrExtractResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client vers le moteur OCR de la DEI (Afriland First Bank — voir guide AFB_GI_OCR_DEI). Sert
 * uniquement l'extraction de documents d'identité (CNI recto/verso) : la reconnaissance faciale
 * ({@code /v1/face/*}) n'est pas déployée sur ce service pour l'instant.
 */
@Component
@Slf4j
public class DeiOcrClient {

    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 2000;
    private static final int TIMEOUT_MS = 60_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;

    public DeiOcrClient(
        @Value("${app.ocr-engine.base-url:http://62.169.26.178:8020}") String baseUrl,
        @Value("${app.ocr-engine.api-key:}") String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "La clé API du moteur OCR DEI (app.ocr-engine.api-key) est obligatoire. "
                    + "Voir le guide d'intégration AFB_GI_OCR_DEI et renseigner OCR_ENGINE_API_KEY."
            );
        }
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public DeiOcrExtractResult extract(byte[] image, String filename, String documentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", namedResource(image, filename));
        body.add("document_type", documentType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-API-Key", apiKey);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        RestClientException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restTemplate.exchange(
                    baseUrl + "/v1/ocr/extract",
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<DeiOcrExtractResult>() {}
                ).getBody();
            } catch (HttpStatusCodeException e) {
                if (!e.getStatusCode().is5xxServerError() || attempt == MAX_ATTEMPTS) {
                    throw translateError(e);
                }
                lastError = e;
            } catch (RestClientException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw translateError(e);
                }
                lastError = e;
            }
            log.warn("Appel au moteur OCR DEI échoué (tentative {}/{}), nouvelle tentative dans {} ms",
                attempt, MAX_ATTEMPTS, RETRY_BACKOFF_MS);
            sleep(RETRY_BACKOFF_MS);
        }
        throw translateError(lastError);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private BusinessException translateError(RestClientException e) {
        if (e instanceof HttpStatusCodeException httpError) {
            if (httpError.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.error("Clé API du moteur OCR DEI refusée (401)");
                return new BusinessException("Le service de vérification de document est mal configuré (clé API invalide).");
            }
            String detail = extractDetail(httpError.getResponseBodyAsString());
            if (detail != null) {
                log.warn("Le moteur OCR DEI a refusé la requête ({}) : {}", httpError.getStatusCode(), detail);
                return new BusinessException(detail);
            }
        }
        log.error("Erreur de communication avec le moteur OCR DEI : {}", e.getMessage());
        return new BusinessException(
            "Erreur de communication avec le service de vérification de document. Réessayez dans quelques instants."
        );
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
