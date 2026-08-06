package com.firstagent.backend.vision.dto;

import java.util.List;

public record DocumentExtractResult(
    ExtractedDocumentFields fields,
    String rawText,
    double averageConfidence,
    List<String> issues) {}
