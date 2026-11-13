package com.platform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalFilter that records request processing timing and logs structured completion metrics
 * including method, route/path, HTTP status, request correlation ID, and elapsed time in milliseconds.
 */
@Component
public class RequestTimingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String method = exchange.getRequest().getMethod().name();
                    String path = exchange.getRequest().getURI().getPath();
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = (statusCode != null) ? statusCode.value() : 500;
                    
                    String correlationId = exchange.getAttribute(CorrelationIdGlobalFilter.CORRELATION_ID_ATTR);
                    if (correlationId == null) {
                        correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
                    }

                    log.info("[Gateway Timing] method={} path={} status={} durationMs={}ms requestId={}",
                            method, path, status, duration, correlationId);
                });
    }

    @Override
    public int getOrder() {
        // Runs immediately after CorrelationIdGlobalFilter
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

