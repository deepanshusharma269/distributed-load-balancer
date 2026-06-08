package com.loadbalancer.health;

import com.loadbalancer.core.BackendNode;
import com.loadbalancer.core.BackendRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckerTest {

    private BackendRegistry registry;
    private BackendNode     node;

    @BeforeEach
    void setUp() {
        registry = new BackendRegistry();
        node     = new BackendNode("node-1", "localhost", 9999);
        registry.register(node);
    }

    @Test
    void healthyProbeKeepsNodeHealthy() throws InterruptedException {
        HealthChecker hc = new HealthChecker(registry, HealthProbe.alwaysHealthy(),
                                              50, 2, 1);
        hc.start();
        Thread.sleep(200);
        hc.stop();
        assertTrue(node.isHealthy(), "Node should remain healthy");
    }

    @Test
    void failingProbeEvictsNodeAfterThreshold() throws InterruptedException {
        // failThreshold=2 → need 2 consecutive failures before eviction
        HealthChecker hc = new HealthChecker(registry, HealthProbe.alwaysUnhealthy(),
                                              50, 2, 1);
        hc.start();
        Thread.sleep(300);   // at least 3 check cycles
        hc.stop();
        assertFalse(node.isHealthy(), "Node should have been evicted");
        assertEquals(0, registry.healthyCount());
    }

    @Test
    void recoveryReinstatesNode() throws InterruptedException {
        AtomicBoolean alive = new AtomicBoolean(false);
        HealthProbe probe   = n -> alive.get();

        HealthChecker hc = new HealthChecker(registry, probe, 50, 2, 1);
        hc.start();

        // Let it fail → evict
        Thread.sleep(300);
        assertFalse(node.isHealthy());

        // Recover
        alive.set(true);
        Thread.sleep(200);
        hc.stop();

        assertTrue(node.isHealthy(), "Node should be reinstated after recovery");
    }
}
