// filepath: /backend/src/main/java/com/example/app/shared/ai/AiLimitProvider.java
package com.example.app.shared.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

/**
 * Batas AI EFEKTIF = nilai bawaan dari konfigurasi (app.ai.*, environment) ditimpa baris di tabel ai_limit_setting.
 * Hasil di-cache {@value #CACHE_MS} ms supaya tidak ada query database di setiap request generate; perubahan lewat menu admin
 * langsung berlaku di instance ini ({@link #invalidate()}) dan paling lambat {@value #CACHE_MS} ms di instance lain.
 * Database bermasalah / nilai di luar rentang TIDAK pernah menggagalkan generate: dipakai nilai terakhir yang baik / bawaan.
 */
@Slf4j
@Component
public class AiLimitProvider {

    static final long CACHE_MS = 10_000L;

    private final AiProperties properties;
    private final AiLimitStore store; // null = hanya nilai bawaan
    private final Clock clock;

    private volatile Snapshot snapshot;
    private volatile long loadedAtMs;

    @Autowired
    public AiLimitProvider(AiProperties properties, ObjectProvider<AiLimitStore> store, Clock clock) {
        this(properties, store.getIfAvailable(), clock);
    }

    public AiLimitProvider(AiProperties properties, AiLimitStore store, Clock clock) {
        this.properties = properties;
        this.store = store;
        this.clock = clock;
    }

    /** Untuk kode/test yang tidak memakai database: persis perilaku lama (hanya app.ai.*). */
    public static AiLimitProvider defaultsOnly(AiProperties properties) {
        return new AiLimitProvider(properties, (AiLimitStore) null, Clock.systemUTC());
    }

    /** Batas efektif satu tier (hanya dibaca; jangan diubah pemanggil). */
    public AiTierProperties tier(AiTier tier) {
        AiTier t = tier == null ? AiTier.FREE : tier.canonical();
        return current().tiers.get(t);
    }

    public int globalDailyLimit() {
        return current().globalDailyLimit;
    }

    public int quotaCircuitMinutes() {
        return current().quotaCircuitMinutes;
    }

    /** Nilai efektif sekarang untuk (scope, kunci) -- scope = nama tier atau "GLOBAL". */
    public int value(String scope, AiLimitKey key) {
        if (key.isGlobal()) {
            return key == AiLimitKey.GLOBAL_DAILY_LIMIT ? globalDailyLimit() : quotaCircuitMinutes();
        }
        return key.read(tier(AiTier.valueOf(scope)));
    }

    /** Nilai bawaan (tanpa database) untuk (scope, kunci). */
    public int defaultValue(String scope, AiLimitKey key) {
        return build(Map.of()).read(scope, key);
    }

    /** Paksa baca ulang database pada pemakaian berikutnya. */
    public void invalidate() {
        loadedAtMs = Long.MIN_VALUE;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s != null && (store == null || clock.millis() - loadedAtMs < CACHE_MS)) {
            return s;
        }
        return reload();
    }

    private synchronized Snapshot reload() {
        long now = clock.millis();
        if (snapshot != null && (store == null || now - loadedAtMs < CACHE_MS)) {
            return snapshot;
        }
        Map<String, Integer> overrides = Map.of();
        if (store != null) {
            try {
                overrides = store.overrides();
            } catch (RuntimeException e) {
                log.warn("[AI-LIMIT] Gagal membaca ai_limit_setting; memakai {}. Penyebab: {}",
                        snapshot != null ? "nilai terakhir yang baik" : "nilai bawaan", e.toString());
                if (snapshot != null) {
                    loadedAtMs = now; // coba lagi setelah jeda cache, bukan di setiap request
                    return snapshot;
                }
            }
        }
        snapshot = build(overrides);
        loadedAtMs = now;
        return snapshot;
    }

    private Snapshot build(Map<String, Integer> overrides) {
        Map<AiTier, AiTierProperties> tiers = new EnumMap<>(AiTier.class);
        for (AiTier tier : AiLimitKey.limitTiers()) {
            AiTierProperties base = (tier == AiTier.FREE ? properties.getFree() : properties.getVip()).copy();
            for (AiLimitKey key : AiLimitKey.tierKeys()) {
                Integer v = overrides.get(key.storageKey(tier.name()));
                if (v != null) {
                    if (key.accepts(v)) key.write(base, v);
                    else warnOutOfRange(key.storageKey(tier.name()), v);
                }
            }
            tiers.put(tier, base);
        }
        int globalDaily = properties.getGlobalDailyLimit();
        int circuit = properties.getQuotaCircuitMinutes();
        Integer gd = overrides.get(AiLimitKey.GLOBAL_DAILY_LIMIT.storageKey(AiLimitKey.GLOBAL_SCOPE));
        if (gd != null) {
            if (AiLimitKey.GLOBAL_DAILY_LIMIT.accepts(gd)) globalDaily = gd; else warnOutOfRange("GLOBAL.GLOBAL_DAILY_LIMIT", gd);
        }
        Integer qc = overrides.get(AiLimitKey.QUOTA_CIRCUIT_MINUTES.storageKey(AiLimitKey.GLOBAL_SCOPE));
        if (qc != null) {
            if (AiLimitKey.QUOTA_CIRCUIT_MINUTES.accepts(qc)) circuit = qc; else warnOutOfRange("GLOBAL.QUOTA_CIRCUIT_MINUTES", qc);
        }
        tiers.put(AiTier.VIP, tiers.get(AiTier.VIP_MONTHLY));
        return new Snapshot(tiers, globalDaily, circuit);
    }

    private static void warnOutOfRange(String key, int value) {
        log.warn("[AI-LIMIT] Nilai {}={} di luar rentang yang diizinkan; diabaikan, dipakai nilai bawaan.", key, value);
    }

    private record Snapshot(Map<AiTier, AiTierProperties> tiers, int globalDailyLimit, int quotaCircuitMinutes) {
        int read(String scope, AiLimitKey key) {
            if (key.isGlobal()) return key == AiLimitKey.GLOBAL_DAILY_LIMIT ? globalDailyLimit : quotaCircuitMinutes;
            return key.read(tiers.get(AiTier.valueOf(scope)));
        }
    }
}
