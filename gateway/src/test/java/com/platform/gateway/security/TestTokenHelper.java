package com.platform.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class TestTokenHelper {

    public static final String TEST_SECRET = "cloud_platform_api_gateway_super_secure_jwt_signing_key_2026_min_256_bits!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    public static String generateValidToken(Long userId, String email, String role) {
        return generateToken(userId, email, role, 3600000L);
    }

    public static String generateExpiredToken(Long userId, String email, String role) {
        return generateToken(userId, email, role, -10000L);
    }

    public static String generateToken(Long userId, String email, String role, long validityMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .issuer("cloud-platform-gateway")
                .subject(userId != null ? userId.toString() : "user-1")
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .claim("name", "Test User")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(KEY, Jwts.SIG.HS256)
                .compact();
    }
}

