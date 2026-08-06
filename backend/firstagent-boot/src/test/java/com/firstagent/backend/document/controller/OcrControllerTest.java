package com.firstagent.backend.document.controller;

import com.firstagent.backend.document.dto.OcrExtractionResponse;
import com.firstagent.backend.document.service.OcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OcrController.class)
class OcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OcrService ocrService;

    private final String sessionToken = "session-token";

    @Test
    void extractReturnsOkWithOcrData() throws Exception {
        when(ocrService.extractDocumentData(sessionToken)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/documents/ocr/extract").header("X-Session-Token", sessionToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.firstName").value("Jean"))
            .andExpect(jsonPath("$.data.status").value("EXTRACTED"));
    }

    @Test
    void confirmWithInvalidPayloadReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/documents/ocr")
                .header("X-Session-Token", sessionToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void confirmWithValidPayloadReturnsOk() throws Exception {
        when(ocrService.confirmExtractedData(any(), any())).thenReturn(sampleResponse());

        String payload = """
            {
                "firstName": "Jean",
                "lastName": "Nkeng",
                "documentNumber": "123456789",
                "birthDate": "1990-01-01",
                "expiryDate": "2030-01-01"
            }
            """;

        mockMvc.perform(put("/api/v1/documents/ocr")
                .header("X-Session-Token", sessionToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
    }

    private OcrExtractionResponse sampleResponse() {
        return OcrExtractionResponse.builder()
            .documentOcrResultId(UUID.randomUUID())
            .firstName("Jean")
            .lastName("Nkeng")
            .documentNumber("123456789")
            .birthDate(LocalDate.of(1990, 1, 1))
            .expiryDate(LocalDate.of(2030, 1, 1))
            .confidenceScore(92.0)
            .status("EXTRACTED")
            .provider("MOCK")
            .build();
    }
}
