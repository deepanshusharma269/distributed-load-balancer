package com.loadbalancer.metrics;

import com.loadbalancer.core.BackendNode;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects throughput and latency metrics across a dispatcher run.
 *
 * <ul>
 *   <li>{@link #recordRequest(BackendNode, long)} — called by the Dispatcher after
 *       each completed request.</li>
 *   <li>{@link #snapshot()} — returns an immutable {@link MetricsSnapshot} safe
 *       to read from any thread at any time.</li>
 * </ul>
 */
public class MetricsCollector {

    private final LongAdder totalRequests  = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LatencyRecorder latencies;

    private volatile long startTimeMs = System.currentTimeMillis();

    public MetricsCollector()               { this(new LatencyRecorder()); }
    public MetricsCollector(LatencyRecorder lr) { this.latencies = lr; }

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    public void recordRequest(BackendNode node, long latencyMs) {
        totalRequests.increment();
        latencies.record(latencyMs);
        if (node != null) node.recordRequest(latencyMs);
    }

    public void recordFailure() { failedRequests.increment(); }

    public void reset() {
        totalRequests.reset();
        failedRequests.reset();
        startTimeMs = System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // Snapshot
    // -------------------------------------------------------------------------

    public MetricsSnapshot snapshot() {
        long elapsed = Math.max(1, System.currentTimeMillis() - startTimeMs);
        long total   = totalRequests.sum();
        long failed  = failedRequests.sum();
        double tps   = total * 1000.0 / elapsed;
        LatencyRecorder.PercentileSnapshot pct = latencies.snapshot();
        return new MetricsSnapshot(total, failed, tps, elapsed, pct);
    }

    // -------------------------------------------------------------------------
    // Snapshot record
    // -------------------------------------------------------------------------

    public record MetricsSnapshot(
            long   totalRequests,
            long   failedRequests,
            double throughputRps,
            long   elapsedMs,
            LatencyRecorder.PercentileSnapshot latency) {

        public String format() {
            return String.format(
                """
                ┌─── Metrics ───────────────────────────────────┐
                │  Total Requests  : %-10d                    │
                │  Failed          : %-10d                    │
                │  Throughput      : %-10.0f req/s            │
                │  Elapsed         : %-10d ms                 │
                │  Latency (ms)                                  │
                │    min  = %-8d                              │
                │    P50  = %-8d                              │
                │    P90  = %-8d                              │
                │    P99  = %-8d                              │
                │    max  = %-8d                              │
                │    mean = %-8.1f                             │
                └────────────────────────────────────────────────┘
                """,
                totalRequests, failedRequests, throughputRps, elapsedMs,
                latency.min(), latency.p50(), latency.p90(),
                latency.p99(), latency.max(), latency.mean());
        }

        /** Compact one-liner for comparison tables. */
        public String compact(String strategyName) {
            return String.format("%-22s │ total=%6d │ tps=%7.0f │ P50=%3d │ P90=%3d │ P99=%3d │ mean=%5.1f",
                strategyName, totalRequests, throughputRps,
                latency.p50(), latency.p90(), latency.p99(), latency.mean());
        }
    }

    // -------------------------------------------------------------------------
    // Per-node report helper
    // -------------------------------------------------------------------------

    public static String nodeReport(List<BackendNode> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s %-16s %8s %10s %12s%n",
                "ID", "Host:Port", "Weight", "Requests", "AvgLatency"));
        sb.append("─".repeat(65)).append('\n');
        for (BackendNode n : nodes) {
            sb.append(String.format("%-12s %-16s %8d %10d %11.1f ms%n",
                    n.getId(),
                    n.getHost() + ":" + n.getPort(),
                    n.getWeight(),
                    n.getTotalRequests(),
                    n.getAverageLatencyMs()));
        }
        return sb.toString();
    }
}
