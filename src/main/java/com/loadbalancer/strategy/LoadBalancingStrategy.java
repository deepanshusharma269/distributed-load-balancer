package com.loadbalancer.strategy;

import com.loadbalancer.core.BackendNode;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for load-balancing algorithms.
 *
 * <p>Implementations receive the live snapshot of healthy nodes on every call.
 * Adding a new algorithm requires only implementing this interface — the
 * {@link com.loadbalancer.core.Dispatcher} and health-check infrastructure need
 * zero modifications (Open/Closed Principle).
 *
 * <p>All implementations must be thread-safe.
 */
public interface LoadBalancingStrategy {

    /**
     * Selects a backend node from the supplied healthy pool.
     *
     * @param healthyNodes immutable snapshot of currently healthy nodes
     * @return the chosen node, or {@link Optional#empty()} if the pool is empty
     */
    Optional<BackendNode> select(List<BackendNode> healthyNodes);

    /** Human-readable name of the strategy (used in logs and metrics output). */
    String name();
}
