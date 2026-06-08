package com.loadbalancer.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lock-efficient latency recorder that supports P50 / P90 / P99 percentile
 * computation over a fixed-size circular sample window.
 *
 * <p>Writes use a {@link LongAdder}-style approach: samples are appended to a
 * pre-allocated {@code long[]} ring-buffer under a read-lock (many writers can
 * proceed concurrently). Percentile computation grabs a write-lock, snapshots
 * the array, sorts, and returns — a brief stop-the-world moment that only
 * happens when the caller explicitly asks for metrics.
 *
 * <p>Ring buffer size defaults to 100 000 samples (~800 KB). Once full the
 * oldest samples are overwritten.
 */
public class LatencyRecorder {

    private final long[]    buffer;
    private final int       capacity;
    private final LongAdder writePos = new LongAdder();  // monotonic counter

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LatencyRecorder()                  { this(100_000); }
    public LatencyRecorder(int capacity)      {
        this.capacity = capacity;
        this.buffer   = new long[capacity];
    }

    /** Record a single latency observation (in milliseconds). */
    public void record(long latencyMs) {
        lock.readLock().lock();
        try {
            // writePos monotonically increases; mod by capacity gives ring index
            int idx = (int)(Math.abs(writePos.intValue()) % capacity);
            writePos.increment();
            buffer[idx] = latencyMs;
        } finally {
            lock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Percentile API
    // -------------------------------------------------------------------------

    public PercentileSnapshot snapshot() {
        lock.writeLock().lock();
        try {
            long count = writePos.sum();
            int  filled = (int) Math.min(count, capacity);
            long[] copy = Arrays.copyOf(buffer, filled);
            Arrays.sort(copy);
            return new PercentileSnapshot(copy, count);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long totalCount() { return writePos.sum(); }

    // -------------------------------------------------------------------------
    // Snapshot record
    // -------------------------------------------------------------------------

    public record PercentileSnapshot(long[] sortedSamples, long totalObservations) {

        public long p50() { return percentile(50); }
        public long p90() { return percentile(90); }
        public long p99() { return percentile(99); }
        public long min() { return sortedSamples.length == 0 ? 0 : sortedSamples[0]; }
        public long max() { return sortedSamples.length == 0 ? 0 : sortedSamples[sortedSamples.length - 1]; }

        public double mean() {
            if (sortedSamples.length == 0) return 0.0;
            long sum = 0;
            for (long v : sortedSamples) sum += v;
            return (double) sum / sortedSamples.length;
        }

        private long percentile(int pct) {
            if (sortedSamples.length == 0) return 0;
            int idx = (int) Math.ceil(pct / 100.0 * sortedSamples.length) - 1;
            return sortedSamples[Math.max(0, Math.min(idx, sortedSamples.length - 1))];
        }

        @Override
        public String toString() {
            return String.format(
                "PercentileSnapshot{n=%d, min=%d, p50=%d, p90=%d, p99=%d, max=%d, mean=%.1f}",
                sortedSamples.length, min(), p50(), p90(), p99(), max(), mean());
        }
    }
}
