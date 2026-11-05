package com.loadbalancer.gateway.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CompletionRequest(
        @NotBlank String prompt,
        @Min(1) @Max(4096) int maxTokens
) {
}
