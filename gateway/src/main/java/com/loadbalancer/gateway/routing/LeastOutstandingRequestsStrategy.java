package com.loadbalancer.gateway.routing;

import com.loadbalancer.gateway.backend.Backend;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LeastOutstandingRequestsStrategy implements RoutingStrategy {
    @Override
    public Optional<Backend> select(List<Backend> healthyBackends) {
        return healthyBackends.stream()
                .min(Comparator.comparingInt(Backend::inFlight));
    }

    @Override
    public String name() {
        return "LeastOutstandingRequests";
    }
}
