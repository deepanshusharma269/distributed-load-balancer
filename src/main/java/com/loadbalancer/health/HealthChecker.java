package com.loadbalancer.health;

import com.loadbalancer.core.BackendNode;
import com.loadbalancer.core.BackendRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodic health-checker that probes every registered backend and evicts
 * (marks unhealthy) nodes that fail successive checks.
 *
 * <p>The actual probe logic is injected via {@link HealthProbe} so production
 * code can use real TCP/HTTP probes while tests inject instant-return stubs.
 *
 * <pre>
 * Config defaults
 *   intervalMs        = 5 000   check every 5 s
 *   failThreshold     = 2       mark unhealthy after 2 consecutive failures
 *   recoverThreshold  = 1       reinstate after 1 consecutive success
 * </pre>
 */
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private final BackendRegistry registry;
    private final HealthProbe     probe;
    private final long            intervalMs;
    private final int             failThreshold;
    private final int             recoverThreshold;

    // Per-node consecutive failure / success counters (not thread-shared, only
    // written by the single scheduler thread)
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> failures  = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> successes = new java.util.concurrent.ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "health-checker");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> task;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public HealthChecker(BackendRegistry registry, HealthProbe probe) {
        this(registry, probe, 5_000, 2, 1);
    }

    public HealthChecker(BackendRegistry registry, HealthProbe probe,
                         long intervalMs, int failThreshold, int recoverThreshold) {
        this.registry         = registry;
        this.probe            = probe;
        this.intervalMs       = intervalMs;
        this.failThreshold    = failThreshold;
        this.recoverThreshold = recoverThreshold;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public synchronized void start() {
        if (task != null) return;
        task = scheduler.scheduleAtFixedRate(this::runChecks, 0, intervalMs, TimeUnit.MILLISECONDS);
        log.info("HealthChecker started (interval={}ms, failThreshold={}, recoverThreshold={})",
                 intervalMs, failThreshold, recoverThreshold);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        scheduler.shutdownNow();
        log.info("HealthChecker stopped");
    }

    // -------------------------------------------------------------------------
    // Core check loop
    // -------------------------------------------------------------------------

    private void runChecks() {
        for (BackendNode node : registry.getAllNodes()) {
            boolean ok;
            try {
                ok = probe.isAlive(node);
            } catch (Exception e) {
                log.debug("Health probe threw for {}: {}", node.getId(), e.getMessage());
                ok = false;
            }

            if (ok) {
                failures.put(node.getId(), 0);
                int streak = successes.merge(node.getId(), 1, Integer::sum);
                if (!node.isHealthy() && streak >= recoverThreshold) {
                    registry.markHealthy(node.getId());
                    successes.put(node.getId(), 0);
                }
            } else {
                successes.put(node.getId(), 0);
                int streak = failures.merge(node.getId(), 1, Integer::sum);
                if (node.isHealthy() && streak >= failThreshold) {
                    registry.markUnhealthy(node.getId());
                    failures.put(node.getId(), 0);
                }
            }
        }
    }
}
