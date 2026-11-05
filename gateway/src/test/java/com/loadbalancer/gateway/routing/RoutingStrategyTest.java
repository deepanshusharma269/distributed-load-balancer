package com.loadbalancer.gateway.routing;

import com.loadbalancer.gateway.backend.Backend;
import com.loadbalancer.gateway.backend.BackendRegistry;
import com.loadbalancer.gateway.config.GatewayProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingStrategyTest {
    private static Backend backend(String id, int weight) {
        return new Backend(id, URI.create("http://localhost:8080"), weight);
    }

    @Test
    void roundRobinDistributesEvenly() {
        List<Backend> backends = List.of(backend("a", 1), backend("b", 1), backend("c", 1));
        RoundRobinStrategy strategy = new RoundRobinStrategy();
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < 600; i++) {
            strategy.select(backends).ifPresent(selected -> counts.merge(selected.id(), 1, Integer::sum));
        }

        assertEquals(Map.of("a", 200, "b", 200, "c", 200), counts);
    }

    @Test
    void weightedRoundRobinRetainsConfiguredDistribution() {
        List<Backend> backends = List.of(backend("a", 3), backend("b", 1), backend("c", 1));
        SmoothWeightedRoundRobinStrategy strategy = new SmoothWeightedRoundRobinStrategy();
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < 500; i++) {
            strategy.select(backends).ifPresent(selected -> counts.merge(selected.id(), 1, Integer::sum));
        }

        assertEquals(Map.of("a", 300, "b", 100, "c", 100), counts);
    }

    @Test
    void leastOutstandingRequestsChoosesLeastLoadedBackend() {
        Backend idle = backend("idle", 1);
        Backend busy = backend("busy", 1);
        busy.incrementInFlight();
        busy.incrementInFlight();

        assertEquals("idle", new LeastOutstandingRequestsStrategy()
                .select(List.of(busy, idle)).orElseThrow().id());
    }

    @Test
    void ewmaAvoidsBackendWithHigherMeasuredLatency() {
        Backend fast = backend("fast", 1);
        Backend slow = backend("slow", 1);
        fast.recordSuccess(20.0, 1.0);
        slow.recordSuccess(200.0, 1.0);
        LatencyAwareEwmaStrategy strategy = new LatencyAwareEwmaStrategy();
        List<Backend> candidates = List.of(fast, slow);

        for (int i = 0; i < 100; i++) {
            assertEquals("fast", strategy.select(candidates).orElseThrow().id());
        }
    }

    @Test
    void passiveFailuresEvictAndActiveChecksRestoreBackend() {
        BackendRegistry registry = new BackendRegistry(new GatewayProperties(
                List.of(new GatewayProperties.BackendConfig("api", URI.create("http://localhost:8080"), 1)),
                null, null, null, 0.3), new SimpleMeterRegistry());
        Backend backend = registry.get("api");

        registry.recordPassiveFailure(backend);
        registry.recordPassiveFailure(backend);
        assertTrue(backend.healthy());
        registry.recordPassiveFailure(backend);
        assertEquals(false, backend.healthy());

        registry.resetHealth();
        registry.recordProbeFailure(backend);
        registry.recordProbeFailure(backend);
        assertTrue(backend.healthy());
        registry.recordProbeFailure(backend);
        assertEquals(false, backend.healthy());

        registry.recordProbeSuccess(backend);
        assertEquals(false, backend.healthy());
        registry.recordProbeSuccess(backend);
        assertTrue(backend.healthy());
    }
}
