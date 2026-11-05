package com.loadbalancer.gateway.routing;

import com.loadbalancer.gateway.backend.Backend;

import java.util.List;
import java.util.Optional;

public interface RoutingStrategy {
    Optional<Backend> select(List<Backend> healthyBackends);

    String name();
}
