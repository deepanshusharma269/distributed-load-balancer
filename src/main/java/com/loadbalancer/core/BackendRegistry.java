package com.loadbalancer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe registry of {@link BackendNode} instances.
 *
 * <p>Provides atomic register / deregister / eviction operations that the
 * health-check subsystem and load-balancing strategies can call concurrently
 * without additional locking.
 */
public class BackendRegistry {

    private static final Logger log = LoggerFactory.getLogger(BackendRegistry.class);

    /** Master map: nodeId → node (includes both healthy and unhealthy nodes). */
    private final Map<String, BackendNode> allNodes = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a new backend node. Idempotent — registering the same ID twice
     * is a no-op and the existing node is preserved.
     */
    public void register(BackendNode node) {
        BackendNode prev = allNodes.putIfAbsent(node.getId(), node);
        if (prev == null) {
            log.info("Registered backend: {}", node);
        } else {
            log.warn("Backend '{}' already registered — ignoring duplicate", node.getId());
        }
    }

    /**
     * Permanently removes a node from the registry.
     *
     * @return the removed node, or empty if not found.
     */
    public Optional<BackendNode> deregister(String nodeId) {
        BackendNode removed = allNodes.remove(nodeId);
        if (removed != null) {
            log.info("Deregistered backend: {}", nodeId);
        }
        return Optional.ofNullable(removed);
    }

    // -------------------------------------------------------------------------
    // Health-check-driven eviction
    // -------------------------------------------------------------------------

    /**
     * Marks a node as unhealthy. The node remains in {@code allNodes} so it can
     * be reinstated without a full re-registration; it is excluded from the
     * healthy set returned by {@link #getHealthyNodes()}.
     */
    public void markUnhealthy(String nodeId) {
        BackendNode node = allNodes.get(nodeId);
        if (node != null && node.isHealthy()) {
            node.setHealthy(false);
            log.warn("Backend evicted (unhealthy): {}", nodeId);
        }
    }

    /** Reinstates a previously unhealthy node back into the healthy pool. */
    public void markHealthy(String nodeId) {
        BackendNode node = allNodes.get(nodeId);
        if (node != null && !node.isHealthy()) {
            node.setHealthy(true);
            log.info("Backend reinstated (healthy): {}", nodeId);
        }
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns an immutable snapshot of ALL registered nodes (healthy + unhealthy). */
    public List<BackendNode> getAllNodes() {
        return Collections.unmodifiableList(new ArrayList<>(allNodes.values()));
    }

    /** Returns an immutable snapshot of currently healthy nodes. */
    public List<BackendNode> getHealthyNodes() {
        return allNodes.values()
                       .stream()
                       .filter(BackendNode::isHealthy)
                       .collect(Collectors.toUnmodifiableList());
    }

    public Optional<BackendNode> findById(String nodeId) {
        return Optional.ofNullable(allNodes.get(nodeId));
    }

    public int healthyCount()   { return (int) allNodes.values().stream().filter(BackendNode::isHealthy).count(); }
    public int totalCount()     { return allNodes.size(); }
}
