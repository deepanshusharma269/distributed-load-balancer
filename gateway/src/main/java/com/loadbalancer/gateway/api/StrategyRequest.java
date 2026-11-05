package com.loadbalancer.gateway.api;

import jakarta.validation.constraints.NotBlank;

public record StrategyRequest(@NotBlank String name) {
}
