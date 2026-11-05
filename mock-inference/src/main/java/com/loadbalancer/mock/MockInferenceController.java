package com.loadbalancer.mock;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MockInferenceController {
    private final MockInferenceService inferenceService;

    public MockInferenceController(MockInferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    @PostMapping("/v1/completions")
    public CompletionResponse complete(@Valid @RequestBody CompletionRequest request)
            throws InterruptedException {
        return inferenceService.complete(request);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/admin/degrade")
    public Map<String, Long> degrade(@Valid @RequestBody DegradeRequest request) {
        inferenceService.degrade(request);
        return Map.of("baseLatencyMs", inferenceService.baseLatencyMs(),
                "perTokenMs", inferenceService.perTokenMs());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(InferenceFailureException.class)
    public ResponseEntity<Map<String, String>> handleInferenceFailure(InferenceFailureException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", exception.getMessage()));
    }
}
