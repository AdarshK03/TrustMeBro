package com.platform.userservice.service;

import com.platform.userservice.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private final String secret = "test_super_secure_jwt_secret_key_with_at_least_32_bytes_length!";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(secret, expirationMs);
    }

    @Test
    void generateToken_ValidClaims() {
        User user = new User("Alice", "alice@example.com", "ADMIN");
        user.setId(42L);

        String token = jwtTokenService.generateToken(user);
        assertNotNull(token);
        assertFalse(token.isBlank());

        assertTrue(jwtTokenService.validateToken(token));

        Claims claims = jwtTokenService.extractClaims(token);
        assertEquals("42", claims.getSubject());
        assertEquals(42, claims.get("userId", Integer.class));
        assertEquals("alice@example.com", claims.get("email", String.class));
        assertEquals("ADMIN", claims.get("role", String.class));
        assertEquals("Alice", claims.get("name", String.class));
        assertEquals("cloud-platform-gateway", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void validateToken_TamperedToken_ReturnsFalse() {
        User user = new User("Bob", "bob@example.com", "CUSTOMER");
        user.setId(7L);
        String token = jwtTokenService.generateToken(user);

        // Tamper with the token signature
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";
        assertFalse(jwtTokenService.validateToken(tamperedToken));
    }

    @Test
    void validateToken_MalformedToken_ReturnsFalse() {
        assertFalse(jwtTokenService.validateToken("not.a.valid.jwt"));
        assertFalse(jwtTokenService.validateToken(""));
        assertFalse(jwtTokenService.validateToken(null));
    }

    @Test
    void validateToken_ExpiredToken_ReturnsFalse() {
        // Token service with -1000ms expiration (already expired)
        JwtTokenService expiredService = new JwtTokenService(secret, -1000L);
        User user = new User("Charlie", "charlie@example.com", "CUSTOMER");
        user.setId(10L);

        String expiredToken = expiredService.generateToken(user);
        assertFalse(jwtTokenService.validateToken(expiredToken));
    }
}

