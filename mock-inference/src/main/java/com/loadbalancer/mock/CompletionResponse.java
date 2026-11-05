package com.loadbalancer.mock;

public record CompletionResponse(String completion, int tokens, String model) {
}
