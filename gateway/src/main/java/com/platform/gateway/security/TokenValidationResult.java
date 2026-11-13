package com.platform.gateway.security;

import io.jsonwebtoken.Claims;

public class TokenValidationResult {

    public enum Status {
        VALID,
        EXPIRED,
        INVALID_SIGNATURE,
        MALFORMED,
        MISSING
    }

    private final Status status;
    private final String message;
    private final Claims claims;

    public TokenValidationResult(Status status, String message, Claims claims) {
        this.status = status;
        this.message = message;
        this.claims = claims;
    }

    public static TokenValidationResult valid(Claims claims) {
        return new TokenValidationResult(Status.VALID, "Token is valid", claims);
    }

    public static TokenValidationResult expired(String message) {
        return new TokenValidationResult(Status.EXPIRED, message, null);
    }

    public static TokenValidationResult invalidSignature(String message) {
        return new TokenValidationResult(Status.INVALID_SIGNATURE, message, null);
    }

    public static TokenValidationResult malformed(String message) {
        return new TokenValidationResult(Status.MALFORMED, message, null);
    }

    public static TokenValidationResult missing(String message) {
        return new TokenValidationResult(Status.MISSING, message, null);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Claims getClaims() {
        return claims;
    }
}

