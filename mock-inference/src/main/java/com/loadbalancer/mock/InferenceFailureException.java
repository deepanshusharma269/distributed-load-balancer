package com.loadbalancer.mock;

public class InferenceFailureException extends RuntimeException {
    public InferenceFailureException(String message) {
        super(message);
    }
}
