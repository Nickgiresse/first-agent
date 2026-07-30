package com.firstagent.backend.document.controller;

import com.firstagent.backend.document.dto.FaceVerificationResponse;
import com.firstagent.backend.document.service.FaceVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaceVerificationController.class)
class FaceVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaceVerificationService faceVerificationService;

    private final String sessionToken = "session-token";

    @Test
    void verifyReturnsOkWithMatchResult() throws Exception {
        when(faceVerificationService.verifyFace(sessionToken)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/documents/face-verification/verify").header("X-Session-Token", sessionToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.matched").value(true))
            .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    void getReturnsOkWithExistingResult() throws Exception {
        when(faceVerificationService.getVerification(sessionToken)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/documents/face-verification").header("X-Session-Token", sessionToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provider").value("MOCK"));
    }

    private FaceVerificationResponse sampleResponse() {
        return FaceVerificationResponse.builder()
            .faceVerificationResultId(UUID.randomUUID())
            .matched(true)
            .similarityScore(95.0)
            .status("VERIFIED")
            .provider("MOCK")
            .build();
    }
}
