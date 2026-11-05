package com.loadbalancer.mock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MockInferenceService {
    private final AtomicLong baseLatencyMs;
    private final AtomicLong perTokenMs;
    private final double errorRate;

    public MockInferenceService(
            @Value("${BASE_LATENCY_MS:50}") long baseLatencyMs,
            @Value("${PER_TOKEN_MS:2}") long perTokenMs,
            @Value("${ERROR_RATE:0.0}") double errorRate) {
        if (baseLatencyMs < 0 || perTokenMs < 0) {
            throw new IllegalArgumentException("Mock latencies must be non-negative");
        }
        if (errorRate < 0.0 || errorRate > 1.0) {
            throw new IllegalArgumentException("ERROR_RATE must be between 0.0 and 1.0");
        }
        this.baseLatencyMs = new AtomicLong(baseLatencyMs);
        this.perTokenMs = new AtomicLong(perTokenMs);
        this.errorRate = errorRate;
    }

    public CompletionResponse complete(CompletionRequest request) throws InterruptedException {
        if (ThreadLocalRandom.current().nextDouble() < errorRate) {
            throw new InferenceFailureException("Simulated inference failure");
        }
        long delay = Math.addExact(baseLatencyMs.get(),
                Math.multiplyExact(perTokenMs.get(), request.maxTokens()));
        Thread.sleep(delay);
        return new CompletionResponse("Fake completion for: " + request.prompt(),
                request.maxTokens(), "mock-inference");
    }

    public void degrade(DegradeRequest request) {
        baseLatencyMs.set(request.baseLatencyMs());
        perTokenMs.set(request.perTokenMs());
    }

    public long baseLatencyMs() {
        return baseLatencyMs.get();
    }

    public long perTokenMs() {
        return perTokenMs.get();
    }
}
