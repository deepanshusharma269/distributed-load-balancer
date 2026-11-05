package com.loadbalancer.gateway.api;

import com.loadbalancer.gateway.backend.BackendRegistry;
import com.loadbalancer.gateway.routing.RoutingStrategyManager;
import com.loadbalancer.gateway.service.CompletionGateway;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
public class GatewayController {
    private final CompletionGateway completionGateway;
    private final BackendRegistry backendRegistry;
    private final RoutingStrategyManager strategyManager;

    public GatewayController(CompletionGateway completionGateway,
                             BackendRegistry backendRegistry,
                             RoutingStrategyManager strategyManager) {
        this.completionGateway = completionGateway;
        this.backendRegistry = backendRegistry;
        this.strategyManager = strategyManager;
    }

    @PostMapping("/v1/completions")
    public Mono<CompletionResponse> complete(@Valid @RequestBody CompletionRequest request) {
        return completionGateway.complete(request);
    }

    @GetMapping("/admin/backends")
    public List<BackendStatus> backends() {
        return backendRegistry.all().stream().map(BackendStatus::from).toList();
    }

    @PostMapping("/admin/strategy")
    public Map<String, String> changeStrategy(@Valid @RequestBody StrategyRequest request) {
        return Map.of("name", strategyManager.set(request.name()).name());
    }
}
