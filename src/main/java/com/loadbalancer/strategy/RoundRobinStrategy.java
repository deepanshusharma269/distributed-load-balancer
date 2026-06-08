package com.loadbalancer.strategy;

import com.loadbalancer.core.BackendNode;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classic Round Robin — distributes requests evenly across all healthy nodes
 * in a circular order.
 *
 * <p>Uses an {@link AtomicInteger} counter to avoid any synchronization overhead
 * while remaining fully thread-safe under high concurrency.
 */
public class RoundRobinStrategy implements LoadBalancingStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Optional<BackendNode> select(List<BackendNode> healthyNodes) {
        if (healthyNodes.isEmpty()) return Optional.empty();

        // Mod by pool size; use Math.abs to guard against integer overflow wrap-around
        int idx = Math.abs(counter.getAndIncrement() % healthyNodes.size());
        return Optional.of(healthyNodes.get(idx));
    }

    @Override
    public String name() { return "RoundRobin"; }
}
