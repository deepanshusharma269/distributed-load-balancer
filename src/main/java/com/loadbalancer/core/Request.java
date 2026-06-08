package com.loadbalancer.core;

/**
 * Immutable value object representing an inbound request.
 *
 * <p>For simulation purposes each request carries a pre-determined
 * {@code simulatedLatencyMs} — the latency that would be observed if
 * this request were routed to a typical backend.  In a real proxy
 * this field would be replaced with actual response-time measurement.
 */
public record Request(String id, long simulatedLatencyMs) {

    /** Constructs a request with a deterministic latency. */
    public Request {
        if (simulatedLatencyMs < 0)
            throw new IllegalArgumentException("latency must be non-negative");
    }

    /** Convenience factory. */
    public static Request of(String id, long latencyMs) {
        return new Request(id, latencyMs);
    }
}
