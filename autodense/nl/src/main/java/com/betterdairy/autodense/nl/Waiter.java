package com.betterdairy.autodense.nl;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

public final class Waiter {
    private Waiter() {}

    public static void until(BooleanSupplier cond, long timeoutMs, String onTimeoutMsg) {
        Instant start = Instant.now();
        while (Duration.between(start, Instant.now()).toMillis() < timeoutMs) {
            if (cond.getAsBoolean()) return;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        throw new IllegalStateException(onTimeoutMsg);
    }
}
