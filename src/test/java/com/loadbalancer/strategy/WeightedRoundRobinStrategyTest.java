package com.loadbalancer.strategy;

import com.loadbalancer.core.BackendNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeightedRoundRobinStrategyTest {

    private WeightedRoundRobinStrategy strategy;

    @BeforeEach
    void setUp() { strategy = new WeightedRoundRobinStrategy(); }

    @Test
    void emptyPoolReturnsEmpty() {
        assertTrue(strategy.select(List.of()).isEmpty());
    }

    @Test
    void respectsWeightRatio() {
        // A:B:C = 3:1:1  →  A should get ~60% of traffic
        List<BackendNode> nodes = List.of(
            new BackendNode("A", "localhost", 8081, 3),
            new BackendNode("B", "localhost", 8082, 1),
            new BackendNode("C", "localhost", 8083, 1)
        );

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 500; i++) {
            String id = strategy.select(nodes).get().getId();
            counts.merge(id, 1, Integer::sum);
        }

        // A should receive 3/5 = 60% → ≈300 ± 5%
        int aCount = counts.getOrDefault("A", 0);
        assertTrue(aCount >= 270 && aCount <= 330,
            "Expected A ≈300 requests, got " + aCount);
    }

    @Test
    void allEqualWeightsActsLikeRoundRobin() {
        List<BackendNode> nodes = List.of(
            new BackendNode("A", "localhost", 8081, 1),
            new BackendNode("B", "localhost", 8082, 1),
            new BackendNode("C", "localhost", 8083, 1)
        );

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 300; i++) {
            counts.merge(strategy.select(nodes).get().getId(), 1, Integer::sum);
        }

        // Each should get exactly 100
        assertEquals(100, counts.get("A"));
        assertEquals(100, counts.get("B"));
        assertEquals(100, counts.get("C"));
    }

    @Test
    void nameIsCorrect() {
        assertEquals("WeightedRoundRobin", strategy.name());
    }
}
