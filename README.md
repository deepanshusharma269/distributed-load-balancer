# Distributed Load Balancer

[![CI](https://github.com/YOUR_USERNAME/distributed-load-balancer/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/distributed-load-balancer/actions)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Maven](https://img.shields.io/badge/build-Maven-orange.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A production-quality, thread-safe Java load balancer featuring three pluggable strategies, automatic health-check-driven node eviction, and P50/P90/P99 latency metrics — built to demonstrate distributed systems design and low-latency concurrency patterns.

---

## Features

| Capability | Details |
|---|---|
| **Strategies** | Round Robin · Weighted Round Robin (Nginx smooth algo) · Least Connections |
| **Health Checks** | Configurable fail/recover thresholds; unhealthy nodes evicted automatically |
| **Metrics** | P50 / P90 / P99 / mean latency · throughput (req/s) · per-backend breakdown |
| **Concurrency** | Fully thread-safe — `AtomicInteger`, `ConcurrentHashMap`, `volatile` strategy field |
| **Extensibility** | `LoadBalancingStrategy` interface — new algorithm = 1 class + 1 factory entry |
| **Simulation** | 10 000 concurrent requests · 4 virtual backends · 3-strategy comparison table |

---

## Project Structure

```
src/main/java/com/loadbalancer/
├── core/
│   ├── BackendNode.java          # Server node with atomic counters
│   ├── BackendRegistry.java      # Thread-safe node registry + eviction
│   ├── Dispatcher.java           # Routes requests via injected strategy
│   └── Request.java              # Immutable request value object
├── strategy/
│   ├── LoadBalancingStrategy.java  # Interface (Open/Closed Principle)
│   ├── RoundRobinStrategy.java
│   ├── WeightedRoundRobinStrategy.java
│   ├── LeastConnectionsStrategy.java
│   └── StrategyFactory.java
├── health/
│   ├── HealthChecker.java        # Scheduled probe loop with streak counters
│   └── HealthProbe.java          # @FunctionalInterface — TCP / stub / custom
├── metrics/
│   ├── LatencyRecorder.java      # Lock-efficient ring-buffer + percentile math
│   └── MetricsCollector.java     # Aggregates throughput + per-node stats
├── simulation/
│   └── LoadBalancerSimulation.java  # 10k-request 3-strategy comparison
└── cli/
    └── LoadBalancerCLI.java      # picocli entry point
```

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Build
```bash
git clone https://github.com/YOUR_USERNAME/distributed-load-balancer.git
cd distributed-load-balancer
mvn clean package -q
```

### Run — Full Simulation (all 3 strategies, 10 000 requests)
```bash
java -jar target/distributed-load-balancer-1.0.0.jar --mode simulate
```

**Sample output:**
```
╔══════════════════════════════════════════════════════════════════════════════╗
║           LOAD BALANCER SIMULATION — STRATEGY COMPARISON                    ║
║  Requests: 10000     Threads: 100       Backends: 4                         ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ Strategy              │ Total    │ TPS       │ P50   │ P90   │ P99   │ Mean   ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ RoundRobin            │ 10000    │ 91823     │ 55    │ 112   │ 140   │ 60.3   ║
║ WeightedRoundRobin    │ 10000    │ 94512     │ 28    │ 58    │ 82    │ 31.5   ║
║ LeastConnections      │ 10000    │ 93100     │ 35    │ 72    │ 130   │ 42.1   ║
╚══════════════════════════════════════════════════════════════════════════════╝

Weighted RR P90 improvement over Round Robin: 48.2%
```

### Run — Single Strategy
```bash
# Round Robin, 5000 requests, 80 threads
java -jar target/distributed-load-balancer-1.0.0.jar \
     --mode single --strategy rr --requests 5000 --threads 80

# Weighted Round Robin
java -jar target/distributed-load-balancer-1.0.0.jar \
     --mode single --strategy wrr --requests 5000

# Least Connections
java -jar target/distributed-load-balancer-1.0.0.jar \
     --mode single --strategy lc
```

### Run Tests
```bash
mvn test
```

---

## Design Decisions

### Why `AtomicInteger` on `BackendNode`?
Active-connection counts are updated on every request from many threads simultaneously. Using `AtomicInteger` avoids locking the whole node while still guaranteeing visibility and atomicity.

### Why Smooth Weighted Round Robin?
Naive WRR (send 3 to A, then 1 to B, then 1 to C) creates bursty traffic. The Nginx-style smooth algorithm interleaves them (A B A C A A B…) giving better latency distribution while maintaining the exact weight ratio over time.

### Why is `Dispatcher` strategy-agnostic?
The `LoadBalancingStrategy` interface is injected at construction time. Adding a new algorithm like `RandomStrategy` or `IPHashStrategy` requires:
1. Implement `LoadBalancingStrategy`
2. Add one case to `StrategyFactory`
3. Zero changes to `Dispatcher`, `HealthChecker`, or `MetricsCollector`

### Health Check Architecture
```
HealthChecker (daemon thread, interval=5s)
  └─ for each node in registry.getAllNodes()
       └─ probe.isAlive(node)
            ├─ success streak >= recoverThreshold → registry.markHealthy()
            └─ failure streak >= failThreshold   → registry.markUnhealthy()
```

---

## Extending the Load Balancer

### Add a new strategy (e.g. Random)
```java
// 1. Implement the interface
public class RandomStrategy implements LoadBalancingStrategy {
    private final Random rng = new Random();

    @Override
    public Optional<BackendNode> select(List<BackendNode> nodes) {
        if (nodes.isEmpty()) return Optional.empty();
        return Optional.of(nodes.get(rng.nextInt(nodes.size())));
    }

    @Override public String name() { return "Random"; }
}

// 2. Wire into StrategyFactory
case "random" -> new RandomStrategy();
```

That's it. Dispatcher, HealthChecker, and MetricsCollector require no changes.

---

## License

MIT — free to use, modify, and distribute. See [LICENSE](LICENSE).
