package com.firstagent.backend.vision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Réponse de POST /v1/ocr/extract du moteur OCR de la DEI (AFB_GI_OCR_DEI). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeiOcrExtractResult(
    @JsonProperty("raw_text") String rawText,
    @JsonProperty("fields") DeiOcrFields fields,
    @JsonProperty("quality") DeiOcrQuality quality,
    @JsonProperty("engine") String engine,
    @JsonProperty("confidence") Double confidence,
    @JsonProperty("document_type") String documentType,
    @JsonProperty("duration_ms") Long durationMs
) {
}
