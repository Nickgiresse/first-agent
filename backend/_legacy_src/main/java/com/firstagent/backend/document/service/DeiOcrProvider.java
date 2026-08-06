package com.firstagent.backend.document.service;

import com.firstagent.backend.vision.client.DeiOcrClient;
import com.firstagent.backend.vision.dto.DeiOcrExtractResult;
import com.firstagent.backend.vision.dto.DeiOcrFields;
import com.firstagent.backend.vision.dto.DeiOcrQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implémentation de {@link OcrProvider} appelant le moteur OCR de la DEI (Afriland First Bank —
 * guide AFB_GI_OCR_DEI, RapidOCR). Un appel par face (CNI_RECTO puis CNI_VERSO) : le verso porte
 * le numéro de document et les dates, le recto les noms et la date de naissance ; les deux
 * résultats se complètent. Ce moteur ne distingue pas CNI définitive / titre provisoire /
 * récépissé (contrairement à l'ancien microservice interne) : seule la CNI classique est
 * supportée ici, cohérent avec les seuls types de document gérés par l'application
 * ({@code DocumentType.CNI_RECTO}/{@code CNI_VERSO}).
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class DeiOcrProvider implements OcrProvider {

    private static final String DOCUMENT_KIND_CNI = "CNI";
    private static final String DOCUMENT_TYPE_RECTO = "CNI_RECTO";
    private static final String DOCUMENT_TYPE_VERSO = "CNI_VERSO";
    private static final String LOW_QUALITY_VERDICT = "LOW_QUALITY";
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private final DeiOcrClient client;

    @Override
    public OcrExtractionResult extractIdentityDocument(byte[] frontImage, byte[] backImage) {
        DeiOcrExtractResult frontResult = client.extract(frontImage, "front.jpg", DOCUMENT_TYPE_RECTO);
        DeiOcrExtractResult backResult = (backImage != null && backImage.length > 0)
            ? client.extract(backImage, "back.jpg", DOCUMENT_TYPE_VERSO)
            : null;

        DeiOcrFields frontFields = frontResult.fields();
        DeiOcrFields backFields = backResult != null ? backResult.fields() : null;

        String firstName = firstNonBlank(field(frontFields, DeiOcrFields::firstName), field(backFields, DeiOcrFields::firstName));
        String lastName = firstNonBlank(field(frontFields, DeiOcrFields::lastName), field(backFields, DeiOcrFields::lastName));
        String sex = firstNonBlank(field(frontFields, DeiOcrFields::sex), field(backFields, DeiOcrFields::sex));
        // Le verso porte le numéro et les dates via la MRZ : prioritaire sur le recto quand disponible.
        String documentNumber = firstNonBlank(field(backFields, DeiOcrFields::documentNumber), field(frontFields, DeiOcrFields::documentNumber));
        LocalDate birthDate = parseDate(firstNonBlank(field(frontFields, DeiOcrFields::birthDate), field(backFields, DeiOcrFields::birthDate)));
        LocalDate expiryDate = parseDate(firstNonBlank(field(backFields, DeiOcrFields::identityExpiryDate), field(frontFields, DeiOcrFields::identityExpiryDate)));

        double frontQuality = qualityScore(frontResult.quality());
        double qualityScore = backResult != null ? (frontQuality + qualityScore(backResult.quality())) / 2.0 : frontQuality;
        double confidence = backResult != null
            ? (nullToZero(frontResult.confidence()) + nullToZero(backResult.confidence())) / 2.0
            : nullToZero(frontResult.confidence());

        List<String> issues = new ArrayList<>();
        collectIssues(issues, frontResult.quality());
        if (backResult != null) {
            collectIssues(issues, backResult.quality());
        }

        return new OcrExtractionResult(
            DOCUMENT_KIND_CNI,
            firstName,
            lastName,
            documentNumber,
            sex,
            birthDate,
            expiryDate,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            confidence,
            qualityScore,
            issues
        );
    }

    @Override
    public String getProviderName() {
        return "DEI_OCR";
    }

    private static String field(DeiOcrFields fields, java.util.function.Function<DeiOcrFields, String> accessor) {
        return fields != null ? accessor.apply(fields) : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }

    private static double nullToZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static double qualityScore(DeiOcrQuality quality) {
        return quality != null && quality.score() != null ? quality.score() : 0.0;
    }

    private static void collectIssues(List<String> issues, DeiOcrQuality quality) {
        if (quality == null) {
            return;
        }
        if (quality.issues() != null) {
            issues.addAll(quality.issues());
        }
        if (LOW_QUALITY_VERDICT.equals(quality.verdict()) && (quality.issues() == null || quality.issues().isEmpty())) {
            issues.add("Qualité d'image insuffisante (score " + quality.score() + "/100)");
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // essaie le format suivant
            }
        }
        log.warn("Date OCR non reconnue, format inattendu : {}", raw);
        return null;
    }
}
