package com.loadbalancer.gateway.routing;

import com.loadbalancer.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RoutingStrategyManager {
    private final Map<String, RoutingStrategy> strategies;
    private final AtomicReference<RoutingStrategy> active;

    public RoutingStrategyManager(GatewayProperties properties) {
        RoutingStrategy roundRobin = new RoundRobinStrategy();
        RoutingStrategy weighted = new SmoothWeightedRoundRobinStrategy();
        RoutingStrategy leastOutstanding = new LeastOutstandingRequestsStrategy();
        RoutingStrategy latencyAware = new LatencyAwareEwmaStrategy();
        strategies = Map.ofEntries(
                Map.entry("roundrobin", roundRobin),
                Map.entry("rr", roundRobin),
                Map.entry("smoothweightedroundrobin", weighted),
                Map.entry("weightedroundrobin", weighted),
                Map.entry("wrr", weighted),
                Map.entry("leastoutstandingrequests", leastOutstanding),
                Map.entry("leastconnections", leastOutstanding),
                Map.entry("latencyawareewma", latencyAware));
        active = new AtomicReference<>(resolve(properties.strategy()));
    }

    public RoutingStrategyManager() {
        this.strategies = Map.ofEntries(
                Map.entry("roundrobin", new RoundRobinStrategy()),
                Map.entry("smoothweightedroundrobin", new SmoothWeightedRoundRobinStrategy()),
                Map.entry("leastoutstandingrequests", new LeastOutstandingRequestsStrategy()),
                Map.entry("latencyawareewma", new LatencyAwareEwmaStrategy()));
        this.active = new AtomicReference<>(resolve("round-robin"));
    }

    public RoutingStrategy current() {
        return active.get();
    }

    public RoutingStrategy set(String name) {
        RoutingStrategy strategy = resolve(name);
        active.set(strategy);
        return strategy;
    }

    public RoutingStrategy resolve(String name) {
        String key = name == null ? "" : name.replace("-", "")
                .replace("_", "").toLowerCase(Locale.ROOT);
        RoutingStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported strategy '" + name
                    + "'. Choose round-robin, smooth-weighted-round-robin, "
                    + "least-outstanding-requests, or latency-aware-ewma");
        }
        return strategy;
    }
}
