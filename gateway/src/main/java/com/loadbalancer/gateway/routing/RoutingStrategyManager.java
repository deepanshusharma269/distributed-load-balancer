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
        strategies = Map.of(
                "roundrobin", new RoundRobinStrategy(),
                "smoothweightedroundrobin", new SmoothWeightedRoundRobinStrategy(),
                "leastoutstandingrequests", new LeastOutstandingRequestsStrategy(),
                "latencyawareewma", new LatencyAwareEwmaStrategy());
        active = new AtomicReference<>(resolve(properties.strategy()));
    }

    public RoutingStrategyManager() {
        this.strategies = Map.of(
                "roundrobin", new RoundRobinStrategy(),
                "smoothweightedroundrobin", new SmoothWeightedRoundRobinStrategy(),
                "leastoutstandingrequests", new LeastOutstandingRequestsStrategy(),
                "latencyawareewma", new LatencyAwareEwmaStrategy());
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
