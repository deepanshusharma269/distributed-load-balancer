package com.loadbalancer.gateway.backend;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class Backend {
    private final String id;
    private final URI url;
    private final int weight;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicReference<Double> ewmaMs = new AtomicReference<>(Double.POSITIVE_INFINITY);
    private volatile boolean healthy = true;

    public Backend(String id, URI url, int weight) {
        this.id = id;
        this.url = url;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    public URI url() {
        return url;
    }

    public int weight() {
        return weight;
    }

    public int inFlight() {
        return inFlight.get();
    }

    public int incrementInFlight() {
        return inFlight.incrementAndGet();
    }

    public int decrementInFlight() {
        return inFlight.updateAndGet(value -> Math.max(0, value - 1));
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long errors() {
        return errors.get();
    }

    public boolean healthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public double ewmaMs() {
        double value = ewmaMs.get();
        return Double.isFinite(value) ? value : 0.0;
    }

    public double selectionEwmaMs() {
        return ewmaMs.get();
    }

    public void recordSuccess(double latencyMs, double alpha) {
        totalRequests.incrementAndGet();
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
        ewmaMs.updateAndGet(previous -> Double.isFinite(previous)
                ? alpha * latencyMs + (1.0 - alpha) * previous
                : latencyMs);
    }

    public int recordFailure() {
        totalRequests.incrementAndGet();
        errors.incrementAndGet();
        consecutiveSuccesses.set(0);
        return consecutiveFailures.incrementAndGet();
    }

    public int recordProbeSuccess() {
        consecutiveFailures.set(0);
        return consecutiveSuccesses.incrementAndGet();
    }

    public int recordProbeFailure() {
        consecutiveSuccesses.set(0);
        return consecutiveFailures.incrementAndGet();
    }

    public void resetHealthStreaks() {
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
    }
}
