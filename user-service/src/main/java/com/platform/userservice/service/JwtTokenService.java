package com.platform.userservice.service;

import com.platform.userservice.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private static final String DEFAULT_SECRET = "cloud_platform_api_gateway_super_secure_jwt_signing_key_2026_min_256_bits!";
    private static final String ISSUER = "cloud-platform-gateway";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${jwt.secret:${JWT_SECRET:}}") String secret,
            @Value("${jwt.expiration-ms:${JWT_EXPIRATION_MS:86400000}}") long expirationMs) {
        
        String effectiveSecret = (secret != null && !secret.isBlank()) ? secret.trim() : DEFAULT_SECRET;
        if (effectiveSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.warn("Configured JWT_SECRET is shorter than 256 bits (32 bytes). Padding to secure size.");
            effectiveSecret = String.format("%-32s", effectiveSecret).replace(' ', 'k');
        }
        this.signingKey = Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        log.info("Initialized JwtTokenService with expirationMs={} and issuer={}", expirationMs, ISSUER);
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .claim("userId", user.getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim("name", user.getName())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        log.debug("Generated signed JWT token for userId={}, role={}", user.getId(), user.getRole());
        return token;
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token.trim());
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token verification failed: {}", ex.getMessage());
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}

