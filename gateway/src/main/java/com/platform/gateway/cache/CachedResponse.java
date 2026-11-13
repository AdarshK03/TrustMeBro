package com.platform.gateway.cache;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public class CachedResponse {

    private final HttpStatus status;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Instant cachedAt;

    public CachedResponse(HttpStatus status, HttpHeaders headers, byte[] body) {
        this.status = status;
        this.headers = new HttpHeaders();
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (!name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING) &&
                    !name.equalsIgnoreCase(HttpHeaders.CONNECTION)) {
                    this.headers.put(name, values);
                }
            });
        }
        this.body = body != null ? body : new byte[0];
        this.cachedAt = Instant.now();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }
}

