package com.platform.gateway.filter;

import com.platform.gateway.cache.CachedResponse;
import com.platform.gateway.cache.GatewayCacheManager;
import com.platform.gateway.config.GatewayTrafficProperties;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductResponseCacheFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ProductResponseCacheFilter.class);
    public static final String X_CACHE_HEADER = "X-Cache";
    public static final String CACHE_HIT = "HIT";
    public static final String CACHE_MISS = "MISS";

    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("^/api/products/(\\d+)$");

    private final GatewayTrafficProperties trafficProperties;
    private final GatewayCacheManager cacheManager;

    public ProductResponseCacheFilter(GatewayTrafficProperties trafficProperties, GatewayCacheManager cacheManager) {
        this.trafficProperties = trafficProperties;
        this.cacheManager = cacheManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!trafficProperties.isCacheEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 1. Invalidation on write requests: POST, PUT, DELETE to /api/products/**
        if (path.startsWith("/api/products") &&
                (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.DELETE.equals(method))) {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                HttpStatusCode status = exchange.getResponse().getStatusCode();
                if (status != null && status.is2xxSuccessful()) {
                    Matcher matcher = PRODUCT_ID_PATTERN.matcher(path);
                    if (matcher.matches()) {
                        Long productId = Long.parseLong(matcher.group(1));
                        cacheManager.invalidateProduct(productId);
                    } else {
                        cacheManager.invalidate("/api/products");
                    }
                }
            }));
        }

        // 2. Only cache GET requests to /api/products or /api/products/{id}
        if (!HttpMethod.GET.equals(method) || (!path.equals("/api/products") && !path.matches("^/api/products/\\d+$"))) {
            return chain.filter(exchange);
        }

        String cacheKey = path;
        CachedResponse cached = cacheManager.get(cacheKey);

        if (cached != null) {
            // Cache Hit: Serve directly from gateway cache without calling downstream
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(cached.getStatus());
            
            cached.getHeaders().forEach((name, values) -> {
                if (!response.getHeaders().containsKey(name)) {
                    response.getHeaders().put(name, values);
                }
            });
            response.getHeaders().set(X_CACHE_HEADER, CACHE_HIT);

            String correlationId = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTR);
            if (correlationId != null) {
                response.getHeaders().set(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, correlationId);
                response.getHeaders().set(CorrelationIdGlobalFilter.REQUEST_ID_HEADER, correlationId);
            }

            DataBuffer buffer = response.bufferFactory().wrap(cached.getBody());
            return response.writeWith(Mono.just(buffer));
        }

        // Cache Miss: Forward request downstream, tag response, and capture body for caching if successful
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(X_CACHE_HEADER, CACHE_MISS);
            return Mono.empty();
        });

        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatusCode statusCode = getStatusCode();
                if (statusCode != null && statusCode.equals(HttpStatus.OK)) {
                    Flux<? extends DataBuffer> flux = Flux.from(body);
                    return DataBufferUtils.join(flux).flatMap(dataBuffer -> {
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        DataBufferUtils.release(dataBuffer);

                        // Store in cache
                        cacheManager.put(cacheKey, new CachedResponse(HttpStatus.OK, getHeaders(), content));

                        // Write to client
                        DataBuffer newBuffer = bufferFactory().wrap(content);
                        return super.writeWith(Mono.just(newBuffer));
                    });
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    @Override
    public int getOrder() {
        // Runs after Rate Limiting (+10) and JWT Authentication (+20)
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}

