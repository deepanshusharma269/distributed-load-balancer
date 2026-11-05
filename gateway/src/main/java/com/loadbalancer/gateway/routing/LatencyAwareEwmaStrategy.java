package com.loadbalancer.gateway.routing;

import com.loadbalancer.gateway.backend.Backend;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class LatencyAwareEwmaStrategy implements RoutingStrategy {
    @Override
    public Optional<Backend> select(List<Backend> healthyBackends) {
        if (healthyBackends.isEmpty()) {
            return Optional.empty();
        }
        if (healthyBackends.size() == 1) {
            return Optional.of(healthyBackends.getFirst());
        }
        int firstIndex = ThreadLocalRandom.current().nextInt(healthyBackends.size());
        int secondIndex = ThreadLocalRandom.current().nextInt(healthyBackends.size() - 1);
        if (secondIndex >= firstIndex) {
            secondIndex++;
        }
        Backend first = healthyBackends.get(firstIndex);
        Backend second = healthyBackends.get(secondIndex);
        return Optional.of(first.selectionEwmaMs() <= second.selectionEwmaMs() ? first : second);
    }

    public void recordLatency(Backend backend, double latencyMs, double alpha) {
        backend.recordSuccess(latencyMs, alpha);
    }

    @Override
    public String name() {
        return "LatencyAwareEwma";
    }
}
