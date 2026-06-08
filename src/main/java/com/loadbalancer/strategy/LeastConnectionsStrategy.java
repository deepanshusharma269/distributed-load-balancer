package com.loadbalancer.strategy;

import com.loadbalancer.core.BackendNode;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Least Connections — routes each new request to the healthy node with the
 * fewest active (in-flight) connections.
 *
 * <p>No mutable state is held in the strategy itself; the active-connection
 * count lives on each {@link BackendNode} as an {@code AtomicInteger}, so this
 * implementation is inherently thread-safe and stateless.
 *
 * <p>When two nodes share the minimum connection count the selection is
 * deterministic (first match in list order) — good enough for even spreading
 * across nodes of equal capacity.
 */
public class LeastConnectionsStrategy implements LoadBalancingStrategy {

    @Override
    public Optional<BackendNode> select(List<BackendNode> healthyNodes) {
        return healthyNodes.stream()
                           .min(Comparator.comparingInt(BackendNode::getActiveConnections));
    }

    @Override
    public String name() { return "LeastConnections"; }
}
