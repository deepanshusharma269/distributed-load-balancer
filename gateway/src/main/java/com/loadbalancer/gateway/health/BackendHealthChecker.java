package com.loadbalancer.gateway.health;

import com.loadbalancer.gateway.backend.Backend;
import com.loadbalancer.gateway.backend.BackendRegistry;
import com.loadbalancer.gateway.config.GatewayProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class BackendHealthChecker {
    private final WebClient webClient;
    private final BackendRegistry backendRegistry;
    private final Duration healthTimeout;

    public BackendHealthChecker(WebClient.Builder webClientBuilder,
                                BackendRegistry backendRegistry,
                                GatewayProperties properties) {
        this.webClient = webClientBuilder.build();
        this.backendRegistry = backendRegistry;
        this.healthTimeout = properties.healthTimeout();
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void checkBackends() {
        for (Backend backend : backendRegistry.all()) {
            probe(backend).subscribe(healthy -> {
                if (healthy) {
                    backendRegistry.recordProbeSuccess(backend);
                } else {
                    backendRegistry.recordProbeFailure(backend);
                }
            });
        }
    }

    private Mono<Boolean> probe(Backend backend) {
        return webClient.get()
                .uri(backend.url().resolve("/health"))
                .retrieve()
                .toBodilessEntity()
                .timeout(healthTimeout)
                .map(ignored -> true)
                .onErrorReturn(false);
    }
}
