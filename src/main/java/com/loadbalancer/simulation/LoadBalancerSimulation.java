package com.loadbalancer.simulation;

import com.loadbalancer.core.BackendNode;
import com.loadbalancer.core.BackendRegistry;
import com.loadbalancer.core.Dispatcher;
import com.loadbalancer.core.Request;
import com.loadbalancer.health.HealthChecker;
import com.loadbalancer.health.HealthProbe;
import com.loadbalancer.metrics.MetricsCollector;
import com.loadbalancer.strategy.LoadBalancingStrategy;
import com.loadbalancer.strategy.RoundRobinStrategy;
import com.loadbalancer.strategy.WeightedRoundRobinStrategy;
import com.loadbalancer.strategy.LeastConnectionsStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Standalone simulation that fires {@value #TOTAL_REQUESTS} requests
 * across {@value #THREADS} virtual threads, under 3 different strategies,
 * and prints a comparison table of P50/P90/P99 latency and throughput.
 *
 * <p>Backend latency profiles are intentionally non-uniform to make the
 * weighted-RR advantage visible:
 * <ul>
 *   <li>backend-A — weight 3, fast  (10–30 ms)</li>
 *   <li>backend-B — weight 1, medium (30–80 ms)</li>
 *   <li>backend-C — weight 1, slow  (80–150 ms)</li>
 *   <li>backend-D — weight 2, medium (20–60 ms)</li>
 * </ul>
 */
public class LoadBalancerSimulation {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerSimulation.class);

    public static final int TOTAL_REQUESTS = 10_000;
    public static final int THREADS        = 100;

    /** Min/max simulated latency per backend (ms). */
    private static final long[][] BACKEND_LATENCY = {
        {10, 30},   // backend-A (fast)
        {30, 80},   // backend-B (medium)
        {80, 150},  // backend-C (slow)
        {20, 60}    // backend-D (medium-fast)
    };
    private static final int[]    WEIGHTS = {3, 1, 1, 2};

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Runs the simulation for all three strategies and returns a formatted
     * comparison report.
     */
    public String run() throws InterruptedException {
        List<MetricsCollector.MetricsSnapshot> snapshots = new ArrayList<>();
        List<String> strategyNames = new ArrayList<>();

        for (LoadBalancingStrategy strategy : strategies()) {
            MetricsCollector metrics = new MetricsCollector();
            BackendRegistry  registry = buildRegistry();

            // Health-check daemon — always-healthy probe for simulation
            HealthChecker hc = new HealthChecker(registry, HealthProbe.alwaysHealthy(),
                                                 60_000, 3, 1);
            hc.start();

            Dispatcher dispatcher = new Dispatcher(registry, strategy, metrics);
            runConcurrent(dispatcher, registry);
            hc.stop();

            snapshots.add(metrics.snapshot());
            strategyNames.add(strategy.name());
            log.info("Completed strategy: {}", strategy.name());
        }

        return buildReport(strategyNames, snapshots);
    }

    // -------------------------------------------------------------------------
    // Simulation internals
    // -------------------------------------------------------------------------

    private void runConcurrent(Dispatcher dispatcher, BackendRegistry registry)
            throws InterruptedException {

        ExecutorService pool    = Executors.newFixedThreadPool(THREADS);
        CountDownLatch  latch   = new CountDownLatch(TOTAL_REQUESTS);
        Random          rand    = new Random(42);
        List<BackendNode> nodes = registry.getAllNodes();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int reqNum  = i;
            final long latMs  = pickLatency(rand, nodes, reqNum);

            pool.submit(() -> {
                try {
                    dispatcher.dispatch(Request.of("req-" + reqNum, latMs));
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean done = latch.await(60, TimeUnit.SECONDS);
        pool.shutdown();
        if (!done) log.warn("Simulation timed out — not all requests completed");
    }

    /**
     * Assigns a backend-specific latency to a request based on which backend
     * a round-robin pre-assignment would hit — this makes latency profiles
     * deterministic and independent of which strategy is under test.
     */
    private long pickLatency(Random rand, List<BackendNode> nodes, int reqNum) {
        int nodeIdx = reqNum % nodes.size();
        long[] range = BACKEND_LATENCY[nodeIdx];
        return range[0] + (long)(rand.nextDouble() * (range[1] - range[0]));
    }

    // -------------------------------------------------------------------------
    // Registry / strategy helpers
    // -------------------------------------------------------------------------

    private static BackendRegistry buildRegistry() {
        BackendRegistry r = new BackendRegistry();
        String[] ids    = {"backend-A", "backend-B", "backend-C", "backend-D"};
        int[]    ports  = {8081, 8082, 8083, 8084};
        for (int i = 0; i < ids.length; i++) {
            r.register(new BackendNode(ids[i], "localhost", ports[i], WEIGHTS[i]));
        }
        return r;
    }

    private static List<LoadBalancingStrategy> strategies() {
        return List.of(
            new RoundRobinStrategy(),
            new WeightedRoundRobinStrategy(),
            new LeastConnectionsStrategy()
        );
    }

    // -------------------------------------------------------------------------
    // Report
    // -------------------------------------------------------------------------

    private String buildReport(List<String> names,
                               List<MetricsCollector.MetricsSnapshot> snaps) {

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║           LOAD BALANCER SIMULATION — STRATEGY COMPARISON                    ║\n");
        sb.append("║  Requests: ").append(String.format("%-8d", TOTAL_REQUESTS))
          .append("  Threads: ").append(String.format("%-8d", THREADS))
          .append("  Backends: 4                         ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ %-22s │ %-8s │ %-9s │ %-5s │ %-5s │ %-5s │ %-6s ║%n",
                "Strategy", "Total", "TPS", "P50", "P90", "P99", "Mean"));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");

        for (int i = 0; i < names.size(); i++) {
            MetricsCollector.MetricsSnapshot s = snaps.get(i);
            sb.append(String.format("║ %-22s │ %-8d │ %-9.0f │ %-5d │ %-5d │ %-5d │ %-6.1f ║%n",
                    names.get(i),
                    s.totalRequests(),
                    s.throughputRps(),
                    s.latency().p50(),
                    s.latency().p90(),
                    s.latency().p99(),
                    s.latency().mean()));
        }

        sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");

        // Highlight WRR vs RR improvement if both present
        if (snaps.size() >= 2) {
            long rrP90  = snaps.get(0).latency().p90();
            long wrrP90 = snaps.get(1).latency().p90();
            if (rrP90 > 0) {
                double improvement = (rrP90 - wrrP90) * 100.0 / rrP90;
                sb.append(String.format("%nWeighted RR P90 improvement over Round Robin: %.1f%%\n",
                        improvement));
            }
        }

        return sb.toString();
    }
}
