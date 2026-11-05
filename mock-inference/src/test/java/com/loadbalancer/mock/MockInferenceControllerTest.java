package com.loadbalancer.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "BASE_LATENCY_MS=0",
        "PER_TOKEN_MS=0",
        "ERROR_RATE=0.0"
})
@AutoConfigureMockMvc
class MockInferenceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockInferenceService inferenceService;

    @BeforeEach
    void resetLatency() {
        inferenceService.degrade(new DegradeRequest(0, 0));
    }

    @Test
    void returnsFakeCompletion() throws Exception {
        mockMvc.perform(post("/v1/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"hello","maxTokens":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion").value("Fake completion for: hello"))
                .andExpect(jsonPath("$.tokens").value(4));
    }

    @Test
    void reportsHealth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void changesLatencyAtRuntime() throws Exception {
        mockMvc.perform(post("/admin/degrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLatencyMs":120,"perTokenMs":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseLatencyMs").value(120))
                .andExpect(jsonPath("$.perTokenMs").value(5));

        org.junit.jupiter.api.Assertions.assertEquals(120, inferenceService.baseLatencyMs());
        org.junit.jupiter.api.Assertions.assertEquals(5, inferenceService.perTokenMs());
    }

    @Test
    void rejectsInvalidCompletionRequest() throws Exception {
        mockMvc.perform(post("/v1/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"","maxTokens":0}
                                """))
                .andExpect(status().isBadRequest());
    }
}
