package com.loadbalancer.gateway.service;

import com.loadbalancer.gateway.api.CompletionRequest;
import com.loadbalancer.gateway.api.CompletionResponse;
import com.loadbalancer.gateway.backend.Backend;
import com.loadbalancer.gateway.backend.BackendRegistry;
import com.loadbalancer.gateway.config.GatewayProperties;
import com.loadbalancer.gateway.routing.RoutingStrategyManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CompletionGateway {
    private final WebClient webClient;
    private final BackendRegistry backendRegistry;
    private final RoutingStrategyManager strategyManager;
    private final MeterRegistry meterRegistry;
    private final Duration requestTimeout;
    private final double ewmaAlpha;

    public CompletionGateway(WebClient.Builder webClientBuilder,
                             BackendRegistry backendRegistry,
                             RoutingStrategyManager strategyManager,
                             GatewayProperties properties,
                             MeterRegistry meterRegistry) {
        this.webClient = webClientBuilder.build();
        this.backendRegistry = backendRegistry;
        this.strategyManager = strategyManager;
        this.meterRegistry = meterRegistry;
        this.requestTimeout = properties.requestTimeout();
        this.ewmaAlpha = properties.ewmaAlpha();
    }

    public Mono<CompletionResponse> complete(CompletionRequest request) {
        AtomicReference<String> outcome = new AtomicReference<>("error");
        Timer.Sample sample = Timer.start(meterRegistry);
        return Mono.defer(() -> attempt(request, new HashSet<>()))
                .doOnSuccess(ignored -> {
                    outcome.set("success");
                    meterRegistry.counter("gateway.requests", "outcome", "success").increment();
                })
                .doOnError(ignored ->
                        meterRegistry.counter("gateway.requests", "outcome", "error").increment())
                .doFinally(signal -> sample.stop(Timer.builder("gateway.request.latency")
                        .description("End-to-end inference request latency")
                        .publishPercentileHistogram()
                        .tag("outcome", outcome.get())
                        .register(meterRegistry)));
    }

    private Mono<CompletionResponse> attempt(CompletionRequest request, Set<String> excluded) {
        Backend backend = strategyManager.current().select(backendRegistry.healthyExcept(excluded))
                .orElseThrow(NoHealthyBackendException::new);
        Set<String> nextExcluded = new HashSet<>(excluded);
        nextExcluded.add(backend.id());

        return invoke(backend, request)
                .onErrorResume(firstFailure -> {
                    try {
                        Backend retry = strategyManager.current()
                                .select(backendRegistry.healthyExcept(nextExcluded))
                                .orElseThrow(() -> new NoHealthyBackendException(firstFailure));
                        return invoke(retry, request)
                                .onErrorMap(secondFailure -> new NoHealthyBackendException(secondFailure));
                    } catch (NoHealthyBackendException noRetryBackend) {
                        return Mono.error(noRetryBackend);
                    }
                });
    }

    private Mono<CompletionResponse> invoke(Backend backend, CompletionRequest request) {
        backend.incrementInFlight();
        long startedAt = System.nanoTime();
        return webClient.post()
                .uri(backend.url().resolve("/v1/completions"))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CompletionResponse.class)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Backend " + backend.id() + " returned an empty completion")))
                .timeout(requestTimeout)
                .doOnNext(response -> backend.recordSuccess(
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), ewmaAlpha))
                .doOnError(ignored -> backendRegistry.recordPassiveFailure(backend))
                .doFinally(ignored -> backend.decrementInFlight());
    }
}
