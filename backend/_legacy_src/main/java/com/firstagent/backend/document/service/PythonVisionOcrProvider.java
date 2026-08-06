package com.firstagent.backend.document.service;

import com.firstagent.backend.vision.client.PythonVisionClient;
import com.firstagent.backend.vision.dto.DocumentAnalyzeResult;
import com.firstagent.backend.vision.dto.DocumentExtractResult;
import com.firstagent.backend.vision.dto.ExtractedDocumentFields;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Implémentation de {@link OcrProvider} appelant le microservice Python (Modules 1 et 2 :
 * qualité du document puis extraction OCR structurée CNI/titre provisoire). Remplace Mindee.
 */
@Service
@RequiredArgsConstructor
public class PythonVisionOcrProvider implements OcrProvider {

    private final PythonVisionClient visionClient;

    @Override
    public OcrExtractionResult extractIdentityDocument(byte[] frontImage, byte[] backImage) {
        DocumentAnalyzeResult frontQuality = visionClient.analyzeDocument(frontImage, "front.jpg");
        double qualityScore = frontQuality.qualityScore();
        if (backImage != null && backImage.length > 0) {
            DocumentAnalyzeResult backQuality = visionClient.analyzeDocument(backImage, "back.jpg");
            qualityScore = (frontQuality.qualityScore() + backQuality.qualityScore()) / 2.0;
        }

        DocumentExtractResult extraction = visionClient.extractDocument(frontImage, backImage);
        ExtractedDocumentFields fields = extraction.fields();

        List<String> issues = new ArrayList<>(extraction.issues());
        if (frontQuality.issues() != null) {
            issues.addAll(frontQuality.issues());
        }

        return new OcrExtractionResult(
            fields.documentKind(),
            fields.firstName(),
            fields.lastName(),
            fields.documentNumber(),
            fields.sex(),
            fields.birthDate(),
            fields.expiryDate(),
            fields.birthPlace(),
            fields.fatherName(),
            fields.motherName(),
            fields.kitNumber(),
            fields.requestIdentifier(),
            fields.paymentAmount(),
            fields.paymentDate(),
            extraction.averageConfidence(),
            qualityScore,
            issues
        );
    }

    @Override
    public String getProviderName() {
        return "PYTHON_VISION";
    }
}
