# AI Inference Gateway

A Java 21 / Spring Boot 3 WebFlux gateway that proxies completion requests to configurable inference backends. The included mock inference service lets you exercise latency, failure, routing, and health behavior without a model provider.

## Architecture

```text
Client
  |
  | POST /v1/completions
  v
Gateway (WebFlux, port 80 in Compose)
  |-- routing strategy + health state + retry/timeout
  |-- WebClient --------------------> mock-fast   (50 ms base latency)
  |-- WebClient --------------------> mock-medium (100 ms base latency)
  |-- WebClient --------------------> mock-slow   (300 ms base latency)
  |
  |-- /admin/backends
  |-- /admin/strategy
  `-- /actuator/prometheus
```

The Maven reactor contains `gateway/` and `mock-inference/`. The gateway probes each backend's `/health` endpoint every two seconds, marks it unhealthy after three consecutive failures, and restores it after two consecutive successes. Failed inference calls also count toward the failure streak. A failed request is retried once on another healthy backend.

## Routing strategies

Select a strategy with `GATEWAY_STRATEGY` or change it at runtime through `POST /admin/strategy`.

| Name | Behavior |
|---|---|
| `round-robin` | Atomic counter distributes requests evenly across healthy backends. |
| `smooth-weighted-round-robin` | Nginx-style smooth weighted rotation using each backend's configured weight. |
| `least-outstanding-requests` | Picks the backend with the fewest in-flight calls. |
| `latency-aware-ewma` | Samples two healthy backends and chooses the lower observed EWMA latency. |

Weights are configured under `gateway.backends` in [application.yml](gateway/src/main/resources/application.yml). Each route tracks in-flight calls and latency; the request path decrements in-flight state in Reactor's `doFinally` hook, including errors and timeouts.

## Run locally

Requires Java 21 and Maven 3.9+. To run the mock services directly, start three instances with distinct `SERVER_PORT` values (the Compose setup below does this automatically). The gateway backend URLs can be overridden with `FAST_BACKEND_URL`, `MEDIUM_BACKEND_URL`, and `SLOW_BACKEND_URL`.

```powershell
mvn --batch-mode clean verify
```

The simplest runnable setup is Docker Compose. It publishes the gateway on port 80 and the mock services on ports 8081–8083:

```sh
docker compose up --build
```

All containers set `JAVA_TOOL_OPTIONS=-Xmx128m`. The mock ports are published for local testing only; do not expose these unauthenticated admin endpoints to untrusted networks. Stop the demo with `docker compose down`.

## API examples

Create a completion:

```sh
curl -X POST http://localhost/v1/completions \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Explain weighted routing","maxTokens":32}'
```

Inspect backend health and counters:

```sh
curl http://localhost/admin/backends
```

Switch the active routing algorithm:

```sh
curl -X POST http://localhost/admin/strategy \
  -H 'Content-Type: application/json' \
  -d '{"name":"latency-aware-ewma"}'
```

Degrade a mock backend at runtime:

```sh
curl -X POST http://localhost:8081/admin/degrade \
  -H 'Content-Type: application/json' \
  -d '{"baseLatencyMs":500,"perTokenMs":10}'
```

Scrape metrics:

```sh
curl http://localhost/actuator/prometheus
```

The gateway returns `503 Service Unavailable` when no healthy backend is available or all allowed attempts fail. Requests exceeding `gateway.request-timeout` also fail over and ultimately return 503 when no retry succeeds.

## Benchmark results

Numbers are intentionally blank until measured in a documented environment.

| Strategy | Throughput (req/s) | P50 (ms) | P90 (ms) | P99 (ms) |
|---|---:|---:|---:|---:|
| Round Robin |  |  |  |  |
| Smooth Weighted Round Robin |  |  |  |  |
| Least Outstanding Requests |  |  |  |  |
| Latency-Aware EWMA |  |  |  |  |

## Limitations

- The mock inference service returns fabricated text; this repository does not connect to a model or implement tokenization, streaming, or model-specific request formats.
- Health and routing state are in-memory and local to each gateway process. There is no shared coordination across gateway replicas.
- Admin routes and actuator metrics have no authentication or authorization. Do not expose them to untrusted networks without adding access controls.
- EWMA needs observations to warm up; the initial latency for an unobserved backend is unknown.
- Retries are limited to one alternate backend and do not provide request deduplication or idempotency guarantees.
- There are no rate limits, admission control, request queue bounds, or durable metrics storage.
