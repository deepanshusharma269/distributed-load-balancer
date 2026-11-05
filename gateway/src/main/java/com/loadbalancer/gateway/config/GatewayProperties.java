package com.loadbalancer.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        List<BackendConfig> backends,
        Duration requestTimeout,
        Duration healthTimeout,
        String strategy,
        double ewmaAlpha
) {
    public GatewayProperties {
        backends = backends == null ? List.of() : List.copyOf(backends);
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(3) : requestTimeout;
        healthTimeout = healthTimeout == null ? Duration.ofSeconds(1) : healthTimeout;
        strategy = strategy == null ? "round-robin" : strategy;
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("gateway.request-timeout must be positive");
        }
        if (healthTimeout.isZero() || healthTimeout.isNegative()) {
            throw new IllegalArgumentException("gateway.health-timeout must be positive");
        }
        if (ewmaAlpha <= 0.0 || ewmaAlpha > 1.0) {
            throw new IllegalArgumentException("gateway.ewma-alpha must be in (0, 1]");
        }
    }

    public record BackendConfig(String id, URI url, int weight) {
        public BackendConfig {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Backend id must not be blank");
            }
            if (url == null || !url.isAbsolute()
                    || !(url.getScheme().equals("http") || url.getScheme().equals("https"))) {
                throw new IllegalArgumentException("Backend URL must be an absolute HTTP(S) URL");
            }
            if (weight < 1) {
                throw new IllegalArgumentException("Backend weight must be at least 1");
            }
        }
    }
}
