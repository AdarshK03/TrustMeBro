package com.platform.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fallback controller providing standardized error payload when downstream services are unreachable.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/service-unavailable")
    public Mono<ResponseEntity<Map<String, Object>>> serviceUnavailable(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = "unknown";
        }

        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        errorDetails.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        errorDetails.put("message", "The requested microservice is currently unreachable or experiencing high latency.");
        errorDetails.put("path", exchange.getRequest().getURI().getPath());
        errorDetails.put("requestId", correlationId);

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorDetails));
    }
}
