package com.platform.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.gateway.config.GatewayTrafficProperties;
import com.platform.gateway.ratelimit.InMemoryRateLimiter;
import com.platform.gateway.ratelimit.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    public static final String X_RATELIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String X_RATELIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String X_RATELIMIT_RESET = "X-RateLimit-Reset";

    private final GatewayTrafficProperties trafficProperties;
    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitingFilter(GatewayTrafficProperties trafficProperties, InMemoryRateLimiter rateLimiter) {
        this.trafficProperties = trafficProperties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!trafficProperties.isRateLimitEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip rate limiting for actuator health endpoints to ensure operational monitoring is never starved
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String clientKey = resolveClientKey(request);
        int maxRequests = trafficProperties.getRateLimitMaxRequests();
        long windowSeconds = trafficProperties.getRateLimitWindowSeconds();

        RateLimitResult result = rateLimiter.checkLimit(clientKey, maxRequests, windowSeconds);

        if (!result.isAllowed()) {
            log.warn("Rate limit rejected request from clientKey={} to path={}", clientKey, path);
            return writeRateLimitExceededResponse(exchange, result, maxRequests, windowSeconds);
        }

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(X_RATELIMIT_LIMIT, String.valueOf(result.getLimit()));
            exchange.getResponse().getHeaders().set(X_RATELIMIT_REMAINING, String.valueOf(result.getRemaining()));
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    private String resolveClientKey(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    private Mono<Void> writeRateLimitExceededResponse(ServerWebExchange exchange,
                                                     RateLimitResult result,
                                                     int maxRequests,
                                                     long windowSeconds) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        response.getHeaders().set(X_RATELIMIT_LIMIT, String.valueOf(result.getLimit()));
        response.getHeaders().set(X_RATELIMIT_REMAINING, "0");
        response.getHeaders().set(X_RATELIMIT_RESET, String.valueOf(result.getRetryAfterSeconds()));

        String correlationId = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTR);
        if (correlationId == null) {
            correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = "rate-limit";
            }
        }
        response.getHeaders().set(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().set(CorrelationIdGlobalFilter.REQUEST_ID_HEADER, correlationId);

        String message = String.format("Rate limit exceeded. Maximum %d requests per %ds allowed. Please retry after %ds.",
                maxRequests, windowSeconds, result.getRetryAfterSeconds());

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        errorAttributes.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        errorAttributes.put("message", message);
        errorAttributes.put("path", exchange.getRequest().getURI().getPath());
        errorAttributes.put("requestId", correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Runs after CorrelationId (HIGHEST_PRECEDENCE) and RequestTiming (HIGHEST_PRECEDENCE + 1)
        // and before JwtAuthenticationFilter (HIGHEST_PRECEDENCE + 20)
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

