// filepath: /backend/src/main/java/com/example/app/shared/ai/AiCircuitBreaker.java
package com.example.app.shared.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Pemutus sederhana: setelah provider melaporkan kuota/saldo habis, panggilan
 * berikutnya ditahan selama beberapa menit supaya aplikasi tidak terus
 * menembak provider yang pasti menolak (dan tidak memenuhi log dengan error yang sama).
 */
public class AiCircuitBreaker {

    private final Clock clock;
    private volatile Instant openUntil = Instant.EPOCH;

    public AiCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    public boolean isOpen() {
        return clock.instant().isBefore(openUntil);
    }

    /** @return true kalau pemutus BARU saja dibuka (dipakai untuk mencatat alert sekali per jendela). */
    public synchronized boolean trip(Duration duration) {
        boolean wasOpen = isOpen();
        openUntil = clock.instant().plus(duration);
        return !wasOpen;
    }
}
