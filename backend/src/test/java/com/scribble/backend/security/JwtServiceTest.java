package com.scribble.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Real secret, real expirations - this is a plain constructor, no Spring context needed.
        jwtService = new JwtService(
                "test-only-secret-key-please-32bytes-minimum",
                360, // guest expiration minutes
                60   // user expiration minutes
        );
    }

    @Test
    void generateGuestToken_verifiesBackToSameSubjectAndGuestRole() {
        String token = jwtService.generateGuestToken("guest-123");

        JwtService.VerifiedIdentity identity = jwtService.verify(token);

        assertEquals("guest-123", identity.subject());
        assertEquals(JwtService.Role.GUEST, identity.role());
    }

    @Test
    void generateUserToken_verifiesBackToSameSubjectAndUserRole() {
        String token = jwtService.generateUserToken("user-456");

        JwtService.VerifiedIdentity identity = jwtService.verify(token);

        assertEquals("user-456", identity.subject());
        assertEquals(JwtService.Role.USER, identity.role());
    }

    @Test
    void verify_rejectsGarbageToken() {
        assertThrows(JwtException.class, () -> jwtService.verify("not-a-real-token"));
    }

    @Test
    void verify_rejectsTokenSignedWithDifferentSecret() {
        JwtService otherService = new JwtService(
                "a-completely-different-secret-key-32bytes-min",
                360, 60
        );
        String token = otherService.generateGuestToken("guest-789");

        assertThrows(JwtException.class, () -> jwtService.verify(token));
    }

    @Test
    void isExpired_trueForExpiredJwtException() {
        ExpiredJwtException expired = new ExpiredJwtException(null, null, "expired");

        assertTrue(jwtService.isExpired(expired));
    }

    @Test
    void isExpired_falseForOtherJwtExceptions() {
        JwtException other = new JwtException("malformed");

        assertFalse(jwtService.isExpired(other));
    }

    @Test
    void expiredToken_actuallyThrowsExpiredJwtExceptionOnVerify() {
        // Expiration of 0 minutes means "expires immediately" - by the time we verify,
        // it should already be in the past.
        JwtService instantExpiryService = new JwtService(
                "test-only-secret-key-please-32bytes-minimum",
                0, 0
        );
        String token = instantExpiryService.generateGuestToken("guest-expiring");

        // Give the clock a moment to move past the expiration instant.
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        JwtException ex = assertThrows(JwtException.class, () -> instantExpiryService.verify(token));
        assertTrue(instantExpiryService.isExpired(ex), "Should be recognized as an expiry, not some other JWT error");
    }

    @Test
    void shortSecret_stillProducesAUsableKey() {
        // The constructor pads secrets under 32 bytes - this should not throw,
        // and tokens should still round-trip correctly.
        JwtService shortSecretService = new JwtService("short", 360, 60);

        String token = shortSecretService.generateGuestToken("guest-1");
        JwtService.VerifiedIdentity identity = shortSecretService.verify(token);

        assertEquals("guest-1", identity.subject());
    }
}