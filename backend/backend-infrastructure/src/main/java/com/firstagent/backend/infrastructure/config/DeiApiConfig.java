package com.firstagent.backend.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class DeiApiConfig {

    @Value("${dei.ocr.base-url:https://ocr.afb-firstagent.com}")
    private String ocrBaseUrl;

    @Value("${dei.ocr.api-key:changeme-ocr-key}")
    private String ocrApiKey;

    @Value("${dei.face.base-url:https://face.afb-firstagent.com}")
    private String faceBaseUrl;

    @Value("${dei.face.api-key:changeme-face-key}")
    private String faceApiKey;

    @Bean("deiOcrWebClient")
    public WebClient deiOcrWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(ocrBaseUrl)
                .defaultHeader("X-API-Key", ocrApiKey)
                .build();
    }

    @Bean("deiFaceWebClient")
    public WebClient deiFaceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(faceBaseUrl)
                .defaultHeader("X-Internal-Api-Key", faceApiKey)
                .build();
    }
}
