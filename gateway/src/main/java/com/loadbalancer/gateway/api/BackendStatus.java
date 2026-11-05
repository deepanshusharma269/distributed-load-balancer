package com.loadbalancer.gateway.api;

import com.loadbalancer.gateway.backend.Backend;

import java.net.URI;

public record BackendStatus(
        String id,
        URI url,
        boolean healthy,
        int inFlight,
        double ewmaMs,
        long totalRequests,
        long errors
) {
    public static BackendStatus from(Backend backend) {
        return new BackendStatus(backend.id(), backend.url(), backend.healthy(),
                backend.inFlight(), backend.ewmaMs(), backend.totalRequests(), backend.errors());
    }
}
