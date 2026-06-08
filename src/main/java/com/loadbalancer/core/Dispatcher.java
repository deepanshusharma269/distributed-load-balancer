package com.loadbalancer.core;

import com.loadbalancer.metrics.MetricsCollector;
import com.loadbalancer.strategy.LoadBalancingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Central dispatcher: the only component that touches both
 * {@link BackendRegistry} and {@link LoadBalancingStrategy}.
 *
 * <p>The {@link LoadBalancingStrategy} is injected at construction time and can
 * be swapped at runtime via {@link #setStrategy(LoadBalancingStrategy)} —
 * no changes required in this class when a new algorithm is introduced.
 *
 * <p>{@link #dispatch(Request)} is fully thread-safe and designed to be called
 * from many concurrent threads.
 */
public class Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private final BackendRegistry  registry;
    private final MetricsCollector metrics;

    /** Guarded by volatile for cheap reads, strategy swap is rare. */
    private volatile LoadBalancingStrategy strategy;

    public Dispatcher(BackendRegistry registry,
                      LoadBalancingStrategy strategy,
                      MetricsCollector metrics) {
        this.registry = registry;
        this.strategy = strategy;
        this.metrics  = metrics;
    }

    // -------------------------------------------------------------------------
    // Core dispatch
    // -------------------------------------------------------------------------

    /**
     * Selects a healthy backend and "routes" the request to it.
     *
     * <p>In simulation mode, "routing" means recording the simulated latency
     * the request carries. In a real proxy you would forward bytes here.
     *
     * @return the chosen backend node, or empty if no healthy nodes are available
     */
    public Optional<BackendNode> dispatch(Request request) {
        List<BackendNode> healthy = registry.getHealthyNodes();

        Optional<BackendNode> choice = strategy.select(healthy);

        if (choice.isEmpty()) {
            log.warn("No healthy backends available — request {} dropped", request.id());
            metrics.recordFailure();
            return Optional.empty();
        }

        BackendNode node = choice.get();
        node.incrementConnections();
        try {
            // Simulate request processing for the latency carried in the request
            simulateProcessing(request.simulatedLatencyMs());
            metrics.recordRequest(node, request.simulatedLatencyMs());
            log.debug("Request {} → {} ({}ms)", request.id(), node.getId(), request.simulatedLatencyMs());
        } finally {
            node.decrementConnections();
        }

        return choice;
    }

    // -------------------------------------------------------------------------
    // Strategy hot-swap
    // -------------------------------------------------------------------------

    public void setStrategy(LoadBalancingStrategy strategy) {
        log.info("Strategy changed: {} → {}", this.strategy.name(), strategy.name());
        this.strategy = strategy;
    }

    public LoadBalancingStrategy getStrategy() { return strategy; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * In simulation mode we don't actually sleep — that would make a 10 k-request
     * test take real-time latency×requests. Instead we just burn a tiny amount of
     * CPU to model concurrency pressure. The latency value is still recorded in
     * metrics for realistic percentile output.
     */
    private void simulateProcessing(long latencyMs) {
        // Intentionally NOT sleeping; latency is injected via Request metadata.
    }
}
