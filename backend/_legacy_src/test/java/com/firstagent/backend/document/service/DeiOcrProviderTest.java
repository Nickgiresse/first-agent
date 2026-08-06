package com.firstagent.backend.document.service;

import com.firstagent.backend.vision.client.DeiOcrClient;
import com.firstagent.backend.vision.dto.DeiOcrExtractResult;
import com.firstagent.backend.vision.dto.DeiOcrFields;
import com.firstagent.backend.vision.dto.DeiOcrQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeiOcrProviderTest {

    @Mock
    private DeiOcrClient client;

    private DeiOcrProvider deiOcrProvider;

    @BeforeEach
    void setUp() {
        deiOcrProvider = new DeiOcrProvider(client);
    }

    @Test
    void extractIdentityDocumentAggregatesRectoAndVerso() {
        byte[] frontBytes = new byte[]{1};
        byte[] backBytes = new byte[]{2};

        DeiOcrFields frontFields = new DeiOcrFields(
            "NKENG", "Jean", "1990-01-01", null, null, null, null, "M", List.of(), null, null, null, null, null, null
        );
        DeiOcrQuality frontQuality = new DeiOcrQuality(
            80, "OK", List.of(), 0.5, 100.0, 0.1
        );
        DeiOcrExtractResult frontResult = new DeiOcrExtractResult(
            "RAW TEXT FRONT", frontFields, frontQuality, "RapidOCR", 95.0, "CNI_RECTO", 1500L
        );

        DeiOcrFields backFields = new DeiOcrFields(
            null, null, null, "123456789", null, "15/05/2020", "15/05/2030", null, List.of(), null, null, null, null, null, null
        );
        DeiOcrQuality backQuality = new DeiOcrQuality(
            70, "OK", List.of(), 0.4, 90.0, 0.2
        );
        DeiOcrExtractResult backResult = new DeiOcrExtractResult(
            "RAW TEXT BACK", backFields, backQuality, "RapidOCR", 90.0, "CNI_VERSO", 1200L
        );

        when(client.extract(eq(frontBytes), eq("front.jpg"), eq("CNI_RECTO"))).thenReturn(frontResult);
        when(client.extract(eq(backBytes), eq("back.jpg"), eq("CNI_VERSO"))).thenReturn(backResult);

        OcrExtractionResult result = deiOcrProvider.extractIdentityDocument(frontBytes, backBytes);

        assertThat(result.documentKind()).isEqualTo("CNI");
        assertThat(result.firstName()).isEqualTo("Jean");
        assertThat(result.lastName()).isEqualTo("NKENG");
        assertThat(result.documentNumber()).isEqualTo("123456789");
        assertThat(result.sex()).isEqualTo("M");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(result.expiryDate()).isEqualTo(LocalDate.of(2030, 5, 15));
        assertThat(result.confidenceScore()).isEqualTo(92.5); // (95.0 + 90.0) / 2
        assertThat(result.documentQualityScore()).isEqualTo(75.0); // (80 + 70) / 2
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void extractIdentityDocumentCollectsIssues() {
        byte[] frontBytes = new byte[]{1};

        DeiOcrFields frontFields = new DeiOcrFields(
            "NKENG", "Jean", "1990/01/01", null, null, null, null, "M", List.of(), null, null, null, null, null, null
        );
        DeiOcrQuality frontQuality = new DeiOcrQuality(
            35, "LOW_QUALITY", List.of("Flou détecté"), 0.2, 10.0, 0.5
        );
        DeiOcrExtractResult frontResult = new DeiOcrExtractResult(
            "RAW TEXT FRONT", frontFields, frontQuality, "RapidOCR", 80.0, "CNI_RECTO", 1500L
        );

        when(client.extract(eq(frontBytes), eq("front.jpg"), eq("CNI_RECTO"))).thenReturn(frontResult);

        OcrExtractionResult result = deiOcrProvider.extractIdentityDocument(frontBytes, null);

        assertThat(result.documentQualityScore()).isEqualTo(35.0);
        assertThat(result.issues()).contains("Flou détecté");
        assertThat(result.birthDate()).isNull(); // parse failed due to unknown date format 1990/01/01
    }
}
