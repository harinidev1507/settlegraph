package com.settlegraph.Artifacts.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtService constructed directly with a test secret — no Spring context.
 * Every "invalid" case here is something an attacker or a stale client can
 * actually send; isValid() is the only gate JwtAuthFilter relies on.
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-0123456789";
    private static final String OTHER_SECRET = "a-completely-different-secret-also-32-bytes-xyz";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void generatedToken_isValid_andCarriesTheEmailAndUserId() {
        String token = jwtService.generateToken("alice@example.com", 7L);

        assertTrue(jwtService.isValid(token));
        assertEquals("alice@example.com", jwtService.extractEmail(token));
        assertEquals(7L, jwtService.extractUserId(token));
    }

    @Test
    void expiredToken_isInvalid() {
        // Same secret, but tokens expire one second before they're issued.
        JwtService alreadyExpired = new JwtService(SECRET, -1_000);
        String token = alreadyExpired.generateToken("alice@example.com", 7L);

        assertFalse(jwtService.isValid(token));
    }

    @Test
    void malformedTokens_areInvalid() {
        assertFalse(jwtService.isValid(""));
        assertFalse(jwtService.isValid("not-a-jwt"));
        assertFalse(jwtService.isValid("aaa.bbb.ccc"));
    }

    @Test
    void tokenSignedWithADifferentSecret_isInvalid() {
        String forged = new JwtService(OTHER_SECRET, 60_000).generateToken("alice@example.com", 7L);

        assertFalse(jwtService.isValid(forged));
    }

    @Test
    void tokenWhosePayloadWasEdited_isInvalid_soAUserIdCantBeSwapped() {
        String[] parts = jwtService.generateToken("alice@example.com", 7L).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String edited = payload.replace("\"userId\":7", "\"userId\":8");
        assertNotEquals(payload, edited, "test setup: the userId claim wasn't found to edit");

        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(edited.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertFalse(jwtService.isValid(tampered));
    }

    @Test
    void unsignedTokenWithAlgNone_isInvalid() {
        String unsigned = Jwts.builder()
                .subject("alice@example.com")
                .claim("userId", 7L)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact(); // no signWith -> "alg":"none"

        assertFalse(jwtService.isValid(unsigned));
    }
}
