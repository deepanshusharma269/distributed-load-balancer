package com.loadbalancer.gateway.api;

public record CompletionResponse(String completion, int tokens, String model) {
}
