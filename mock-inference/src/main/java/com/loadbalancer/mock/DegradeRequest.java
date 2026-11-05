package com.loadbalancer.mock;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DegradeRequest(
        @Min(0) @Max(60000) long baseLatencyMs,
        @Min(0) @Max(60000) long perTokenMs
) {
}
