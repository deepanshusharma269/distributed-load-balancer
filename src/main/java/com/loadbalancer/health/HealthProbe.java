package com.loadbalancer.health;

import com.loadbalancer.core.BackendNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Functional interface for a single health probe invocation.
 *
 * <p>Swap implementations for tests (lambda stubs) or for production
 * (HTTP/TCP probes) without touching {@link HealthChecker}.
 */
@FunctionalInterface
public interface HealthProbe {

    /**
     * @return {@code true} if the node is reachable / healthy
     */
    boolean isAlive(BackendNode node) throws Exception;

    // -------------------------------------------------------------------------
    // Built-in implementations
    // -------------------------------------------------------------------------

    /**
     * TCP-connect probe: attempts a socket connection to host:port.
     * Useful for real (non-simulated) deployments.
     */
    static HealthProbe tcp(int timeoutMs) {
        return node -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(node.getHost(), node.getPort()), timeoutMs);
                return true;
            } catch (IOException e) {
                return false;
            }
        };
    }

    /** Always-healthy probe — useful for simulations where backends are virtual. */
    static HealthProbe alwaysHealthy() {
        return node -> true;
    }

    /** Always-unhealthy probe — useful for testing eviction logic. */
    static HealthProbe alwaysUnhealthy() {
        return node -> false;
    }
}
