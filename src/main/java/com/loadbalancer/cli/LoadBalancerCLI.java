package com.loadbalancer.cli;

import com.loadbalancer.simulation.LoadBalancerSimulation;
import com.loadbalancer.core.BackendNode;
import com.loadbalancer.core.BackendRegistry;
import com.loadbalancer.core.Dispatcher;
import com.loadbalancer.core.Request;
import com.loadbalancer.health.HealthChecker;
import com.loadbalancer.health.HealthProbe;
import com.loadbalancer.metrics.MetricsCollector;
import com.loadbalancer.strategy.LoadBalancingStrategy;
import com.loadbalancer.strategy.StrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name        = "load-balancer",
    mixinStandardHelpOptions = true,
    version     = "1.0.0",
    description = "Distributed Load Balancer — Round Robin / Weighted RR / Least Connections"
)
public class LoadBalancerCLI implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerCLI.class);

    @Option(names = {"-m", "--mode"},
            description = "Mode: 'simulate' (default) or 'single'",
            defaultValue = "simulate")
    private String mode;

    @Option(names = {"-s", "--strategy"},
            description = "Strategy: rr | wrr | lc  (used in 'single' mode)",
            defaultValue = "rr")
    private String strategyName;

    @Option(names = {"-n", "--requests"},
            description = "Number of requests to fire in single mode",
            defaultValue = "1000")
    private int numRequests;

    @Option(names = {"-t", "--threads"},
            description = "Worker threads in single mode",
            defaultValue = "50")
    private int threads;

    public static void main(String[] args) {
        int exit = new CommandLine(new LoadBalancerCLI()).execute(args);
        System.exit(exit);
    }

    @Override
    public Integer call() throws Exception {
        if ("simulate".equalsIgnoreCase(mode)) {
            runSimulation();
        } else {
            runSingle();
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // Full 3-strategy comparison simulation
    // ------------------------------------------------------------------

    private void runSimulation() throws InterruptedException {
        System.out.println("\n=== Running full strategy comparison simulation ===\n");
        LoadBalancerSimulation sim = new LoadBalancerSimulation();
        String report = sim.run();
        System.out.println(report);
    }

    // ------------------------------------------------------------------
    // Single strategy run
    // ------------------------------------------------------------------

    private void runSingle() throws InterruptedException {
        System.out.printf("%nRunning %d requests with strategy '%s' on %d threads%n%n",
                numRequests, strategyName, threads);

        BackendRegistry registry = new BackendRegistry();
        registry.register(new BackendNode("backend-A", "localhost", 8081, 3));
        registry.register(new BackendNode("backend-B", "localhost", 8082, 1));
        registry.register(new BackendNode("backend-C", "localhost", 8083, 1));
        registry.register(new BackendNode("backend-D", "localhost", 8084, 2));

        HealthChecker hc = new HealthChecker(registry, HealthProbe.alwaysHealthy(), 60_000, 3, 1);
        hc.start();

        LoadBalancingStrategy strategy = StrategyFactory.create(strategyName);
        MetricsCollector metrics = new MetricsCollector();
        Dispatcher dispatcher = new Dispatcher(registry, strategy, metrics);

        java.util.Random rand = new java.util.Random(42);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(numRequests);

        for (int i = 0; i < numRequests; i++) {
            final int n = i;
            final long lat = 10 + (long)(rand.nextDouble() * 140);
            pool.submit(() -> {
                try { dispatcher.dispatch(Request.of("req-" + n, lat)); }
                finally { latch.countDown(); }
            });
        }
        latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();
        hc.stop();

        MetricsCollector.MetricsSnapshot snap = metrics.snapshot();
        System.out.println(snap.format());
        System.out.println("\nPer-backend breakdown:");
        System.out.println(MetricsCollector.nodeReport(registry.getAllNodes()));
    }
}
