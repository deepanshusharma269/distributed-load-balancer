package com.loadbalancer.gateway.backend;

import com.loadbalancer.gateway.config.GatewayProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BackendRegistry {
    private final Map<String, Backend> backends;

    public BackendRegistry(GatewayProperties properties, MeterRegistry meterRegistry) {
        if (properties.backends().isEmpty()) {
            throw new IllegalArgumentException("At least one gateway backend must be configured");
        }
        Map<String, Backend> configured = new java.util.LinkedHashMap<>();
        for (GatewayProperties.BackendConfig config : properties.backends()) {
            Backend backend = new Backend(config.id(), config.url(), config.weight());
            if (configured.putIfAbsent(backend.id(), backend) != null) {
                throw new IllegalArgumentException("Duplicate backend id: " + backend.id());
            }
            Gauge.builder("gateway.backend.in_flight", backend, Backend::inFlight)
                    .tag("backend", backend.id())
                    .register(meterRegistry);
        }
        this.backends = new ConcurrentHashMap<>(configured);
    }

    public List<Backend> all() {
        return backends.values().stream()
                .sorted(java.util.Comparator.comparing(Backend::id))
                .toList();
    }

    public List<Backend> healthyExcept(java.util.Set<String> excludedIds) {
        return backends.values().stream()
                .filter(Backend::healthy)
                .filter(backend -> !excludedIds.contains(backend.id()))
                .sorted(java.util.Comparator.comparing(Backend::id))
                .toList();
    }

    public Backend get(String id) {
        Backend backend = backends.get(id);
        if (backend == null) {
            throw new IllegalArgumentException("Unknown backend: " + id);
        }
        return backend;
    }

    public void recordProbeSuccess(Backend backend) {
        if (backend.recordProbeSuccess() >= 2 && !backend.healthy()) {
            backend.setHealthy(true);
            backend.resetHealthStreaks();
        }
    }

    public void recordProbeFailure(Backend backend) {
        if (backend.recordProbeFailure() >= 3 && backend.healthy()) {
            backend.setHealthy(false);
            backend.resetHealthStreaks();
        }
    }

    public void recordPassiveFailure(Backend backend) {
        if (backend.recordFailure() >= 3 && backend.healthy()) {
            backend.setHealthy(false);
            backend.resetHealthStreaks();
        }
    }

    public void resetHealth() {
        backends.values().forEach(backend -> {
            backend.setHealthy(true);
            backend.resetHealthStreaks();
        });
    }
}
