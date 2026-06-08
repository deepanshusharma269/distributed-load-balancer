package com.loadbalancer.strategy;

/**
 * Simple factory that maps strategy names (CLI flag values) to
 * {@link LoadBalancingStrategy} instances.
 *
 * <p>New strategies only need to be wired in here and nowhere else.
 */
public final class StrategyFactory {

    private StrategyFactory() {}

    public static LoadBalancingStrategy create(String name) {
        return switch (name.toLowerCase()) {
            case "rr", "round-robin", "roundrobin"            -> new RoundRobinStrategy();
            case "wrr", "weighted-rr", "weightedroundrobin"   -> new WeightedRoundRobinStrategy();
            case "lc", "least-connections", "leastconnections"-> new LeastConnectionsStrategy();
            default -> throw new IllegalArgumentException(
                    "Unknown strategy '" + name + "'. Valid options: rr | wrr | lc");
        };
    }
}
