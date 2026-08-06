package com.firstagent.backend.infrastructure.adapter.out.vision;

import com.firstagent.backend.application.dto.OcrExtractionResponse;
import com.firstagent.backend.application.port.out.PythonVisionPort;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class PythonVisionAdapter implements PythonVisionPort {

    private static final Logger log = LoggerFactory.getLogger(PythonVisionAdapter.class);

    private final WebClient webClient;
    private final String apiKey;

    public PythonVisionAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${app.vision-service.base-url:http://localhost:8001}") String baseUrl,
            @Value("${app.vision-service.api-key:changeme-local-dev-key}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<OcrExtractionResponse> extractDocument(byte[] frontBytes, byte[] backBytes) {
        if (frontBytes == null || frontBytes.length == 0) {
            log.warn("Aucune image de CNI recto fournie à PythonVisionAdapter");
            return Mono.empty();
        }

        log.info("Envoi des images au moteur OCR DEI (POST /v1/ocr/extract)... Recto: {} octets", frontBytes.length);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new NamedByteArrayResource(frontBytes, "cni_recto.jpg"))
                .contentType(MediaType.IMAGE_JPEG);
        builder.part("document_type", "CNI_RECTO");
        builder.part("account_type", "PERSONAL");

        if (backBytes != null && backBytes.length > 0) {
            builder.part("file_back", new NamedByteArrayResource(backBytes, "cni_verso.jpg"))
                    .contentType(MediaType.IMAGE_JPEG);
        }

        return webClient.post()
                .uri("/v1/ocr/extract")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> mapToOcrResponse((Map<String, Object>) map))
                .doOnNext(res -> log.info("Réponse reçue du moteur OCR DEI -> Nom: {}, Prénom: {}, N°Doc: {}", res.lastName(), res.firstName(), res.documentNumber()))
                .doOnError(err -> log.error("Erreur lors de l'appel au moteur OCR DEI : {}", err.getMessage(), err))
                .onErrorResume(e -> fallbackLocalExtract(frontBytes, backBytes));
    }

    private Mono<OcrExtractionResponse> fallbackLocalExtract(byte[] frontBytes, byte[] backBytes) {
        log.info("Tentative de repli (fallback) sur le service d'extraction local/python...");
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("front", new NamedByteArrayResource(frontBytes, "front.jpg"))
                .contentType(MediaType.IMAGE_JPEG);
        if (backBytes != null && backBytes.length > 0) {
            builder.part("back", new NamedByteArrayResource(backBytes, "back.jpg"))
                    .contentType(MediaType.IMAGE_JPEG);
        }
        return webClient.post()
                .uri("/api/v1/document/extract")
                .header("X-Internal-Api-Key", apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> mapToOcrResponse((Map<String, Object>) map))
                .onErrorResume(e -> Mono.empty());
    }

    @SuppressWarnings("unchecked")
    private OcrExtractionResponse mapToOcrResponse(Map<String, Object> responseMap) {
        Map<String, Object> fields = (Map<String, Object>) responseMap.get("fields");
        if (fields == null) {
            fields = Map.of();
        }

        Number confidenceNum = (Number) responseMap.get("confidence");
        if (confidenceNum == null) confidenceNum = (Number) responseMap.get("averageConfidence");
        double confidence = confidenceNum != null ? confidenceNum.doubleValue() : 90.0;

        Map<String, Object> qualityMap = (Map<String, Object>) responseMap.get("quality");
        Number qualityScoreNum = qualityMap != null ? (Number) qualityMap.get("score") : null;
        double qualityScore = qualityScoreNum != null ? qualityScoreNum.doubleValue() : confidence;

        String docKind = strOrNull(fields.get("documentKind"));
        if (docKind == null) docKind = strOrNull(fields.get("document_type"));
        if (docKind == null) docKind = "CNI";

        String firstName = strOrNull(fields.get("firstName"));
        if (firstName == null) firstName = strOrNull(fields.get("first_name"));

        String lastName = strOrNull(fields.get("lastName"));
        if (lastName == null) lastName = strOrNull(fields.get("last_name"));

        String docNumber = strOrNull(fields.get("documentNumber"));
        if (docNumber == null) docNumber = strOrNull(fields.get("identity_document_number"));

        String sex = strOrNull(fields.get("sex"));
        String birthDate = strOrNull(fields.get("birthDate"));
        if (birthDate == null) birthDate = strOrNull(fields.get("birth_date"));

        String expiryDate = strOrNull(fields.get("expiryDate"));
        if (expiryDate == null) expiryDate = strOrNull(fields.get("expiry_date"));

        String birthPlace = strOrNull(fields.get("birthPlace"));
        if (birthPlace == null) birthPlace = strOrNull(fields.get("birth_place"));

        String fatherName = strOrNull(fields.get("fatherName"));
        String motherName = strOrNull(fields.get("motherName"));
        String kitNumber = strOrNull(fields.get("kitNumber"));
        String requestIdentifier = strOrNull(fields.get("requestIdentifier"));
        String paymentAmount = strOrNull(fields.get("paymentAmount"));
        String paymentDate = strOrNull(fields.get("paymentDate"));

        String engine = strOrNull(responseMap.get("engine"));
        String provider = engine != null ? "DEI_OCR_" + engine : "DEI_OCR";

        return new OcrExtractionResponse(
                UUID.randomUUID().toString(),
                docKind,
                firstName,
                lastName,
                docNumber,
                sex,
                birthDate,
                expiryDate,
                birthPlace,
                fatherName,
                motherName,
                kitNumber,
                requestIdentifier,
                paymentAmount,
                paymentDate,
                confidence,
                qualityScore,
                "EXTRACTED",
                provider
        );
    }

    private String strOrNull(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim();
        return s.isBlank() || "UNKNOWN".equalsIgnoreCase(s) ? null : s;
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        public NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }
    }
}
