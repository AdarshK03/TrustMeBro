package com.platform.gateway.errorhandling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.gateway.filter.CorrelationIdGlobalFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Centralized reactive ErrorWebExceptionHandler for Spring Cloud Gateway.
 * Intercepts downstream connectivity failures, timeouts, and route errors,
 * producing standardized, meaningful JSON responses with correlation IDs.
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String error;
        String message;

        String exMsg = ex.getMessage() != null ? ex.getMessage() : "";
        String exClassName = ex.getClass().getName();

        if (ex instanceof ConnectException || (ex.getCause() != null && ex.getCause() instanceof ConnectException)
                || exClassName.contains("ConnectException") || exMsg.contains("Connection refused")) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            error = HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase();
            message = "The downstream microservice is currently unreachable or starting up. Please try again shortly.";
            log.warn("Downstream service unreachable for path {}: {}", exchange.getRequest().getURI().getPath(), ex.getMessage());
        } else if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)
                || exClassName.contains("TimeoutException") || exMsg.contains("timeout") || exMsg.contains("Timeout")) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            error = HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase();
            message = "The request to the downstream microservice timed out.";
            log.warn("Downstream request timed out for path {}: {}", exchange.getRequest().getURI().getPath(), ex.getMessage());
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            error = status.getReasonPhrase();
            message = (rse.getReason() != null) ? rse.getReason() : ex.getMessage();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            error = HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
            message = "An unexpected error occurred while processing the gateway request.";
            log.error("Unhandled gateway error on path {}: {}", exchange.getRequest().getURI().getPath(), ex.getMessage(), ex);
        }

        String correlationId = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTR);
        if (correlationId == null) {
            correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER);
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = "unknown";
            }
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().set(CorrelationIdGlobalFilter.REQUEST_ID_HEADER, correlationId);

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", error);
        errorAttributes.put("message", message);
        errorAttributes.put("path", exchange.getRequest().getURI().getPath());
        errorAttributes.put("requestId", correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}

