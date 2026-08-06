package com.firstagent.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "app.jwt.secret=test-secret-that-is-long-enough-for-hmac-sha256",
      "spring.flyway.enabled=false"
    })
@AutoConfigureMockMvc
class ApiDocumentationAccessTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void swaggerUiIsPubliclyAccessible() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }

  @Test
  void openApiSpecificationIsPubliclyAccessible() throws Exception {
    mockMvc.perform(get("/api-docs")).andExpect(status().isOk());
  }

  @Test
  void malformedJsonReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/accounts/verify")
                .contentType("application/json")
                .content("{invalid-json}"))
        .andExpect(status().isBadRequest());
  }
}
