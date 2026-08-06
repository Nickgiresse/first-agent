package com.firstagent.backend.document.model;

public record FaceMatchResult(boolean matched, double similarityScore, double targetQualityScore) {}
