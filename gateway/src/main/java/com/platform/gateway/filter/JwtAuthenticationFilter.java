package com.platform.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.gateway.config.GatewayTrafficProperties;
import com.platform.gateway.security.JwtValidator;
import com.platform.gateway.security.TokenValidationResult;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private final GatewayTrafficProperties trafficProperties;
    private final JwtValidator jwtValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(GatewayTrafficProperties trafficProperties, JwtValidator jwtValidator) {
        this.trafficProperties = trafficProperties;
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!trafficProperties.isSecurityJwtEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (isPublicRoute(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            log.warn("Missing or invalid Authorization header on protected route: {} {}", method, path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Authorization header with Bearer token is required");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        TokenValidationResult result = jwtValidator.validateToken(token);

        if (!result.isValid()) {
            log.warn("Rejected request to {} {} due to invalid JWT: {}", method, path, result.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, result.getMessage());
        }

        Claims claims = result.getClaims();
        String role = claims.get("role", String.class);
        String userId = claims.get("userId") != null ? claims.get("userId").toString() : claims.getSubject();
        String email = claims.get("email", String.class);

        // Role-based authorization
        if (!isAuthorized(path, method, role, userId)) {
            log.warn("Forbidden: User id={} role={} has insufficient permissions for {} {}", userId, role, method, path);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "Access denied: insufficient permissions for this resource");
        }

        // Mutate request with user identity headers for downstream microservices
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        if (userId != null) {
            requestBuilder.header(USER_ID_HEADER, userId);
        }
        if (email != null) {
            requestBuilder.header(USER_EMAIL_HEADER, email);
        }
        if (role != null) {
            requestBuilder.header(USER_ROLE_HEADER, role);
        }

        log.debug("Authenticated request {} {} for userId={}, role={}", method, path, userId, role);
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private boolean isPublicRoute(String path, HttpMethod method) {
        // User login
        if (path.equals("/api/users/login") || path.startsWith("/api/users/login/")) {
            return true;
        }
        // User registration
        if (path.equals("/api/users") && HttpMethod.POST.equals(method)) {
            return true;
        }
        // Product read operations
        if (path.startsWith("/api/products") && HttpMethod.GET.equals(method)) {
            return true;
        }
        // Actuator health & info
        if (path.startsWith("/actuator")) {
            return true;
        }
        // Gateway fallbacks
        if (path.startsWith("/fallback")) {
            return true;
        }
        // Microservice health checks routed via Gateway
        if (path.startsWith("/api/health")) {
            return true;
        }
        // Gateway diagnostic statistics
        if (path.startsWith("/api/gateway")) {
            return true;
        }
        return false;
    }

    private boolean isAuthorized(String path, HttpMethod method, String role, String userId) {
        if (role == null) {
            return false;
        }
        String normalizedRole = role.toUpperCase();

        // Product write operations require ADMIN role
        if (path.startsWith("/api/products") && (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.DELETE.equals(method))) {
            return "ADMIN".equals(normalizedRole);
        }

        // User list operations require ADMIN role
        if (path.equals("/api/users") && HttpMethod.GET.equals(method)) {
            return "ADMIN".equals(normalizedRole);
        }

        // User single detail GET /api/users/{id}: ADMIN or user accessing their own profile
        if (path.matches("^/api/users/\\d+$") && HttpMethod.GET.equals(method)) {
            if ("ADMIN".equals(normalizedRole) || "SUPPORT".equals(normalizedRole)) {
                return true;
            }
            String targetId = path.substring(path.lastIndexOf('/') + 1);
            return targetId.equals(userId);
        }

        // Orders endpoints are accessible to authenticated CUSTOMER, ADMIN, or SUPPORT
        if (path.startsWith("/api/orders")) {
            return "CUSTOMER".equals(normalizedRole) || "ADMIN".equals(normalizedRole) || "SUPPORT".equals(normalizedRole);
        }

        return true;
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTR);
        if (correlationId == null) {
            correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = "auth-error";
            }
        }
        response.getHeaders().set(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().set(CorrelationIdGlobalFilter.REQUEST_ID_HEADER, correlationId);

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", status.getReasonPhrase());
        errorAttributes.put("message", message);
        errorAttributes.put("path", exchange.getRequest().getURI().getPath());
        errorAttributes.put("requestId", correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Runs after CorrelationId (HIGHEST_PRECEDENCE) and RequestTiming (HIGHEST_PRECEDENCE + 1)
        // and after RateLimiter (HIGHEST_PRECEDENCE + 10)
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}

