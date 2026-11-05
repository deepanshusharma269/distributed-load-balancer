package com.loadbalancer.gateway.routing;

import com.loadbalancer.gateway.backend.Backend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SmoothWeightedRoundRobinStrategy implements RoutingStrategy {
    private final Map<String, Long> currentWeights = new HashMap<>();

    @Override
    public synchronized Optional<Backend> select(List<Backend> healthyBackends) {
        if (healthyBackends.isEmpty()) {
            return Optional.empty();
        }
        Set<String> liveIds = new HashSet<>();
        long totalWeight = 0;
        for (Backend backend : healthyBackends) {
            liveIds.add(backend.id());
            totalWeight += backend.weight();
            currentWeights.merge(backend.id(), (long) backend.weight(), Long::sum);
        }
        currentWeights.keySet().retainAll(liveIds);

        Backend selected = healthyBackends.getFirst();
        for (Backend backend : healthyBackends) {
            if (currentWeights.get(backend.id()) > currentWeights.get(selected.id())) {
                selected = backend;
            }
        }
        currentWeights.merge(selected.id(), -totalWeight, Long::sum);
        return Optional.of(selected);
    }

    @Override
    public String name() {
        return "SmoothWeightedRoundRobin";
    }
}
