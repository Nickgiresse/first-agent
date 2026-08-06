package com.firstagent.backend.document.service;

public record FaceMatchResult(boolean matched, double similarityScore, double targetQualityScore) {
}
