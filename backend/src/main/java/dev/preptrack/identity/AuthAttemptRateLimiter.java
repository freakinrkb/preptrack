package dev.preptrack.identity;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** A bounded per-process limiter for credential and invite-token attempts. */
@Component
final class AuthAttemptRateLimiter {
    private static final long WINDOW_NANOS = Duration.ofMinutes(15).toNanos();
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new LinkedHashMap<>(128, 0.75f, true);

    synchronized boolean allow(String key, int limit) {
        long now = System.nanoTime();
        Window window = windows.get(key);
        if (window == null || now - window.startedAtNanos() >= WINDOW_NANOS) {
            windows.put(key, new Window(now, 1));
            trim();
            return true;
        }
        if (window.attempts() >= limit) return false;
        windows.put(key, new Window(window.startedAtNanos(), window.attempts() + 1));
        return true;
    }

    private void trim() {
        long now = System.nanoTime();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtNanos() >= WINDOW_NANOS);
        while (windows.size() > MAX_KEYS) windows.remove(windows.keySet().iterator().next());
    }

    private record Window(long startedAtNanos, int attempts) {}
}
