package com.loadbalancer.strategy;

import com.loadbalancer.core.BackendNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTest {

    private RoundRobinStrategy strategy;
    private List<BackendNode> nodes;

    @BeforeEach
    void setUp() {
        strategy = new RoundRobinStrategy();
        nodes = List.of(
            new BackendNode("A", "localhost", 8081),
            new BackendNode("B", "localhost", 8082),
            new BackendNode("C", "localhost", 8083)
        );
    }

    @Test
    void emptyPoolReturnsEmpty() {
        assertTrue(strategy.select(List.of()).isEmpty());
    }

    @Test
    void cyclesAcrossAllNodes() {
        // First full round
        assertEquals("A", strategy.select(nodes).get().getId());
        assertEquals("B", strategy.select(nodes).get().getId());
        assertEquals("C", strategy.select(nodes).get().getId());
        // Second round wraps
        assertEquals("A", strategy.select(nodes).get().getId());
    }

    @Test
    void distributesEvenly() {
        int[] counts = new int[3];
        List<BackendNode> pool = nodes;
        for (int i = 0; i < 300; i++) {
            Optional<BackendNode> chosen = strategy.select(pool);
            assertTrue(chosen.isPresent());
            counts[pool.indexOf(chosen.get())]++;
        }
        // Each node should receive exactly 100 requests
        assertArrayEquals(new int[]{100, 100, 100}, counts);
    }

    @Test
    void singleNodeAlwaysChosen() {
        List<BackendNode> single = List.of(new BackendNode("solo", "h", 1));
        for (int i = 0; i < 10; i++) {
            assertEquals("solo", strategy.select(single).get().getId());
        }
    }

    @Test
    void nameIsCorrect() {
        assertEquals("RoundRobin", strategy.name());
    }
}
