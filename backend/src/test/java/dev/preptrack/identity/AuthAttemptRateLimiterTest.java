package dev.preptrack.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthAttemptRateLimiterTest {
    @Test
    void limitsAttemptsPerKeyWithoutBlockingOtherAccounts() {
        var limiter = new AuthAttemptRateLimiter();

        assertTrue(limiter.allow("login:one@example.test", 2));
        assertTrue(limiter.allow("login:one@example.test", 2));
        assertFalse(limiter.allow("login:one@example.test", 2));
        assertTrue(limiter.allow("login:two@example.test", 2));
    }
}
