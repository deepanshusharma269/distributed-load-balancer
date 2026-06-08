package com.loadbalancer.strategy;

import com.loadbalancer.core.BackendNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Smooth Weighted Round Robin (Nginx-style).
 *
 * <p>Each backend is assigned a static weight (e.g. 3:1:1). The algorithm
 * maintains a "current weight" per node and on every selection:
 * <ol>
 *   <li>Increments every node's current weight by its configured weight.</li>
 *   <li>Picks the node with the highest current weight.</li>
 *   <li>Decrements the winner's current weight by the total weight sum.</li>
 * </ol>
 *
 * <p>This produces a maximally smooth distribution — a 3:1:1 configuration will
 * yield the sequence A,A,B,A,C,A,A rather than the bursty A,A,A,B,C,A,A,A…
 * produced by naive weighted RR.
 *
 * <p><b>Thread-safety</b>: the mutable {@code currentWeights} map is guarded
 * by {@code synchronized(this)} since the select operation is not atomically
 * decomposable.
 */
public class WeightedRoundRobinStrategy implements LoadBalancingStrategy {

    // nodeId → running "current weight" (modified each round)
    private final Map<String, Integer> currentWeights = new HashMap<>();

    @Override
    public synchronized Optional<BackendNode> select(List<BackendNode> healthyNodes) {
        if (healthyNodes.isEmpty()) return Optional.empty();

        // Ensure every live node has an entry (handles new nodes after registry changes)
        for (BackendNode n : healthyNodes) {
            currentWeights.putIfAbsent(n.getId(), 0);
        }

        int totalWeight = healthyNodes.stream().mapToInt(BackendNode::getWeight).sum();

        // Step 1 – add static weight to each node's current weight
        for (BackendNode n : healthyNodes) {
            currentWeights.merge(n.getId(), n.getWeight(), Integer::sum);
        }

        // Step 2 – pick node with highest current weight
        BackendNode best = null;
        int bestWeight = Integer.MIN_VALUE;
        for (BackendNode n : healthyNodes) {
            int cw = currentWeights.get(n.getId());
            if (cw > bestWeight) {
                bestWeight = cw;
                best = n;
            }
        }

        // Step 3 – subtract total weight from winner
        if (best != null) {
            currentWeights.merge(best.getId(), -totalWeight, Integer::sum);
        }

        return Optional.ofNullable(best);
    }

    @Override
    public String name() { return "WeightedRoundRobin"; }
}
