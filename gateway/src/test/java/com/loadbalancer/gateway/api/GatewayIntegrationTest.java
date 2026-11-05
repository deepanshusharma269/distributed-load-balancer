package com.loadbalancer.gateway.api;

import com.loadbalancer.gateway.backend.BackendRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.strategy=least-outstanding-requests",
        "gateway.request-timeout=5s",
        "gateway.health-timeout=1s"
})
@AutoConfigureWebTestClient
class GatewayIntegrationTest {
    private static final AtomicBoolean FAST_BACKEND_FAILS = new AtomicBoolean();
    private static final MockWebServer fastServer = server("fast");
    private static final MockWebServer mediumServer = server("medium");
    private static final MockWebServer slowServer = server("slow");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private BackendRegistry backendRegistry;

    @Value("${local.server.port}")
    private int localPort;

    private WebClient loadClient;

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("gateway.backends[0].id", () -> "fast");
        registry.add("gateway.backends[0].url", () -> fastServer.url("/").toString());
        registry.add("gateway.backends[0].weight", () -> "3");
        registry.add("gateway.backends[1].id", () -> "medium");
        registry.add("gateway.backends[1].url", () -> mediumServer.url("/").toString());
        registry.add("gateway.backends[1].weight", () -> "2");
        registry.add("gateway.backends[2].id", () -> "slow");
        registry.add("gateway.backends[2].url", () -> slowServer.url("/").toString());
        registry.add("gateway.backends[2].weight", () -> "1");
    }

    private static MockWebServer server(String id) {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/health".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(200);
                }
                if ("fast".equals(id) && FAST_BACKEND_FAILS.get()) {
                    return new MockResponse().setResponseCode(500);
                }
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBodyDelay(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .setBody("""
                                {"completion":"ok-%s","tokens":1,"model":"test"}
                                """.formatted(id));
            }
        });
        try {
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start mock backend " + id, exception);
        }
        return server;
    }

    @AfterAll
    static void shutdownServers() throws IOException {
        fastServer.shutdown();
        mediumServer.shutdown();
        slowServer.shutdown();
    }

    @BeforeEach
    void resetState() {
        loadClient = WebClient.builder().baseUrl("http://localhost:" + localPort).build();
        FAST_BACKEND_FAILS.set(false);
        backendRegistry.resetHealth();
        webTestClient.post().uri("/admin/strategy")
                .bodyValue(new StrategyRequest("least-outstanding-requests"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void proxiesCompletionAndExposesBackendStatus() {
        long requestsBefore = backendRegistry.get("fast").totalRequests();
        webTestClient.post().uri("/v1/completions")
                .bodyValue(new CompletionRequest("prompt", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.completion").isEqualTo("ok-fast");

        webTestClient.get().uri("/admin/backends")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].url").exists()
                .jsonPath("$[0].healthy").isEqualTo(true)
                .jsonPath("$[0].totalRequests").isEqualTo(requestsBefore + 1);
    }

    @Test
    void retriesAnotherBackendAfterFailure() throws Exception {
        FAST_BACKEND_FAILS.set(true);

        webTestClient.post().uri("/v1/completions")
                .bodyValue(new CompletionRequest("retry", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.completion").isEqualTo("ok-medium");

        assertNotNull(fastServer.takeRequest());
        assertNotNull(mediumServer.takeRequest());
    }

    @Test
    void returnsServiceUnavailableWhenNoBackendIsHealthy() {
        backendRegistry.all().forEach(backend -> backend.setHealthy(false));

        webTestClient.post().uri("/v1/completions")
                .bodyValue(new CompletionRequest("unavailable", 1))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").exists();
    }

    @Test
    void switchesRoutingStrategyAtRuntime() {
        webTestClient.post().uri("/admin/strategy")
                .bodyValue(new StrategyRequest("smooth-weighted-round-robin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("SmoothWeightedRoundRobin");
    }

    @Test
    void exposesPrometheusMetrics() {
        webTestClient.post().uri("/v1/completions")
                .bodyValue(new CompletionRequest("metrics", 1))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertNotNull(body);
                    org.junit.jupiter.api.Assertions.assertTrue(body.contains("gateway_requests_total"));
                    org.junit.jupiter.api.Assertions.assertTrue(body.contains("gateway_request_latency_seconds"));
                    org.junit.jupiter.api.Assertions.assertTrue(body.contains("gateway_backend_in_flight"));
                });
    }

    @Test
    void servesTwoHundredConcurrentRequestsAndReleasesInFlightCounts() {
        List<Integer> statuses = Flux.range(0, 200)
                .flatMap(index -> loadClient
                                .post()
                                .uri("/v1/completions")
                                .bodyValue(new CompletionRequest("parallel-" + index, 1))
                                .retrieve()
                                .bodyToMono(CompletionResponse.class)
                                .map(ignored -> 200)
                                .onErrorReturn(500),
                        200)
                .collectList()
                .block(Duration.ofSeconds(20));

        assertNotNull(statuses);
        assertEquals(200, statuses.size());
        assertEquals(200, statuses.stream().filter(status -> status == 200).count());
        assertEquals(0, backendRegistry.all().stream().mapToInt(backend -> backend.inFlight()).sum());
    }

}
