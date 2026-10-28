// filepath: /backend/src/main/java/com/example/app/shared/ai/AiGlobalBudget.java
package com.example.app.shared.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pengaman biaya seluruh sistem: total generate hari ini LINTAS fitur dibandingkan dengan app.ai.global-daily-limit
 * (0 = tanpa batas). Dilampaui = permintaan baru ditolak dgn pesan umum (urusan operasional, bukan salah user).
 */
@Slf4j
@Service
public class AiGlobalBudget {

    private final AiLimitProvider limits;
    private final List<AiUsageCounter> counters;
    private final Clock clock;

    /** Dipakai Spring. ObjectProvider: aman walau belum ada fitur yang mendaftarkan counter. */
    @Autowired
    public AiGlobalBudget(AiLimitProvider limits, ObjectProvider<AiUsageCounter> counters, Clock clock) {
        this(limits, counters.orderedStream().toList(), clock);
    }

    /** Tanpa batas dari database (hanya app.ai.*): dipakai test dan kode non-Spring. */
    public AiGlobalBudget(AiProperties properties, List<AiUsageCounter> counters, Clock clock) {
        this(AiLimitProvider.defaultsOnly(properties), counters, clock);
    }

    public AiGlobalBudget(AiLimitProvider limits, List<AiUsageCounter> counters, Clock clock) {
        this.limits = limits;
        this.counters = counters;
        this.clock = clock;
    }

    public LocalDateTime startOfToday() {
        return LocalDate.now(clock).atStartOfDay();
    }

    public long usedToday() {
        LocalDateTime since = startOfToday();
        return counters.stream().mapToLong(counter -> counter.countSince(since)).sum();
    }

    /** true = batas global hari ini tercapai (dan mencatat alert ERROR). */
    public boolean isExceeded() {
        int limit = limits.globalDailyLimit();
        if (limit <= 0) {
            return false;
        }
        long used = usedToday();
        if (used >= limit) {
            log.error("[AI-ALERT] Batas generate harian SELURUH sistem ({}) tercapai ({} terpakai, semua fitur AI). "
                    + "Generate ditolak sampai besok atau sampai batas global dinaikkan (menu admin Batas AI atau app.ai.global-daily-limit).", limit, used);
            return true;
        }
        return false;
    }
}
