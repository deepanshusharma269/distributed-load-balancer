package com.loadbalancer.gateway.routing;

import com.loadbalancer.gateway.backend.Backend;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinStrategy implements RoutingStrategy {
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Optional<Backend> select(List<Backend> healthyBackends) {
        if (healthyBackends.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod(counter.getAndIncrement(), healthyBackends.size());
        return Optional.of(healthyBackends.get(index));
    }

    @Override
    public String name() {
        return "RoundRobin";
    }
}
