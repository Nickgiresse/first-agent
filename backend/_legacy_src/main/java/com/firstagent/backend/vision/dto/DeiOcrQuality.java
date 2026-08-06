package com.firstagent.backend.vision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeiOcrQuality(
    @JsonProperty("score") Integer score,
    @JsonProperty("verdict") String verdict,
    @JsonProperty("issues") List<String> issues,
    @JsonProperty("brightness") Double brightness,
    @JsonProperty("laplacian_variance") Double laplacianVariance,
    @JsonProperty("glare_fraction") Double glareFraction
) {
}
