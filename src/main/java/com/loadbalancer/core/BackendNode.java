package com.loadbalancer.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single backend server node.
 *
 * <p>All mutable state (active connections, request count) is updated atomically
 * so BackendNode instances are safe to share across threads without external locking.
 */
public class BackendNode {

    private final String id;
    private final String host;
    private final int    port;
    private final int    weight;   // used by WeightedRoundRobin

    // Thread-safe counters
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong    totalRequests     = new AtomicLong(0);
    private final AtomicLong    totalLatencyMs    = new AtomicLong(0);

    /** Whether this node is currently considered healthy. Volatile for visibility. */
    private volatile boolean healthy = true;

    public BackendNode(String id, String host, int port, int weight) {
        if (weight < 1) throw new IllegalArgumentException("weight must be >= 1");
        this.id   = Objects.requireNonNull(id);
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.weight = weight;
    }

    /** Convenience constructor with weight = 1. */
    public BackendNode(String id, String host, int port) {
        this(id, host, port, 1);
    }

    // -------------------------------------------------------------------------
    // Connection tracking
    // -------------------------------------------------------------------------

    public void incrementConnections() { activeConnections.incrementAndGet(); }
    public void decrementConnections() { activeConnections.decrementAndGet(); }
    public int  getActiveConnections() { return activeConnections.get(); }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    public void recordRequest(long latencyMs) {
        totalRequests.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
    }

    public long getTotalRequests()  { return totalRequests.get(); }
    public long getTotalLatencyMs() { return totalLatencyMs.get(); }

    public double getAverageLatencyMs() {
        long reqs = totalRequests.get();
        return reqs == 0 ? 0.0 : (double) totalLatencyMs.get() / reqs;
    }

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    public boolean isHealthy()          { return healthy; }
    public void    setHealthy(boolean h){ this.healthy = h; }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getId()     { return id;     }
    public String getHost()   { return host;   }
    public int    getPort()   { return port;   }
    public int    getWeight() { return weight; }

    @Override
    public String toString() {
        return String.format("BackendNode{id='%s', host='%s:%d', weight=%d, healthy=%b, activeConn=%d}",
                id, host, port, weight, healthy, activeConnections.get());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackendNode n)) return false;
        return id.equals(n.id);
    }

    @Override public int hashCode() { return id.hashCode(); }
}
