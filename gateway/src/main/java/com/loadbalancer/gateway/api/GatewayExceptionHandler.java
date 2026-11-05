package com.loadbalancer.gateway.api;

import com.loadbalancer.gateway.service.NoHealthyBackendException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.Map;

@RestControllerAdvice
public class GatewayExceptionHandler {
    @ExceptionHandler(NoHealthyBackendException.class)
    public ResponseEntity<Map<String, String>> noHealthyBackend(NoHealthyBackendException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ServerWebInputException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
