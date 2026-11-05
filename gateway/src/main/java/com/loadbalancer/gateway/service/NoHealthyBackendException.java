package com.loadbalancer.gateway.service;

public class NoHealthyBackendException extends RuntimeException {
    public NoHealthyBackendException() {
        super("No healthy inference backend is available");
    }

    public NoHealthyBackendException(Throwable cause) {
        super("Inference backends are unavailable", cause);
    }
}
