package com.firstagent.backend.vision.dto;

import java.util.List;

public record DocumentAnalyzeResult(
    boolean documentDetected,
    String documentType,
    boolean typeConfirmed,
    String side,
    int qualityScore,
    List<String> issues) {}
