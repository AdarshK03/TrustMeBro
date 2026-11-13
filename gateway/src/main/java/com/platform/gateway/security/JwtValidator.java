package com.platform.gateway.security;

import com.platform.gateway.config.GatewayTrafficProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
    private final GatewayTrafficProperties trafficProperties;

    public JwtValidator(GatewayTrafficProperties trafficProperties) {
        this.trafficProperties = trafficProperties;
    }

    private SecretKey getSigningKey() {
        String secret = trafficProperties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            secret = "cloud_platform_api_gateway_super_secure_jwt_signing_key_2026_min_256_bits!";
        }
        byte[] bytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            secret = String.format("%-32s", secret.trim()).replace(' ', 'k');
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public TokenValidationResult validateToken(String token) {
        if (token == null || token.isBlank()) {
            return TokenValidationResult.missing("Missing Bearer authentication token");
        }

        try {
            Claims claims = io.jsonwebtoken.Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();

            return TokenValidationResult.valid(claims);
        } catch (ExpiredJwtException ex) {
            log.warn("JWT validation failed: token is expired (expired at {})", ex.getClaims().getExpiration());
            return TokenValidationResult.expired("JWT token has expired");
        } catch (SecurityException ex) {
            log.warn("JWT validation failed: invalid signature or tampered token: {}", ex.getMessage());
            return TokenValidationResult.invalidSignature("Invalid JWT signature: token may be tampered");
        } catch (MalformedJwtException ex) {
            log.warn("JWT validation failed: malformed token structure: {}", ex.getMessage());
            return TokenValidationResult.malformed("Malformed JWT token format");
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT validation failed: unsupported token: {}", ex.getMessage());
            return TokenValidationResult.malformed("Unsupported JWT token");
        } catch (Exception ex) {
            log.warn("JWT validation failed: unexpected error: {}", ex.getMessage());
            return TokenValidationResult.malformed("Invalid JWT token: " + ex.getMessage());
        }
    }
}
