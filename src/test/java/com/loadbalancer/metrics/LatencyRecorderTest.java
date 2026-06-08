package com.loadbalancer.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LatencyRecorderTest {

    @Test
    void emptyRecorderReturnsZeros() {
        LatencyRecorder.PercentileSnapshot snap = new LatencyRecorder().snapshot();
        assertEquals(0, snap.p50());
        assertEquals(0, snap.p99());
        assertEquals(0.0, snap.mean());
    }

    @Test
    void percentileValuesAreCorrect() {
        LatencyRecorder rec = new LatencyRecorder();
        // Record 100 values: 1..100
        for (int i = 1; i <= 100; i++) rec.record(i);

        LatencyRecorder.PercentileSnapshot snap = rec.snapshot();
        assertEquals(50,  snap.p50());
        assertEquals(90,  snap.p90());
        assertEquals(99,  snap.p99());
        assertEquals(1,   snap.min());
        assertEquals(100, snap.max());
        assertEquals(50.5, snap.mean(), 0.01);
    }

    @Test
    void totalCountIsAccurate() {
        LatencyRecorder rec = new LatencyRecorder();
        for (int i = 0; i < 42; i++) rec.record(10);
        assertEquals(42, rec.totalCount());
    }

    @Test
    void ringBufferWrapsWithoutException() {
        // Buffer of 10, record 25 — should not throw
        LatencyRecorder rec = new LatencyRecorder(10);
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 25; i++) rec.record(i);
        });
    }
}
