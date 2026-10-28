// filepath: /backend/src/test/java/com/example/app/shared/ai/AiLimitProviderTest.java
package com.example.app.shared.ai;

import com.example.app.modules.automation.AutomationTestSupport.MutableClock;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Dilewati sementara atas permintaan. Hapus baris @Disabled di bawah untuk mengaktifkan kembali.
@Disabled("Dilewati sementara: AiLimitProviderTest")
class AiLimitProviderTest {

    private final AiProperties props = new AiProperties(); // bawaan kode: FREE harian 3 / VIP harian 30
    private final MutableClock clock = new MutableClock("2026-10-28T01:00:00Z");

    /** Store uji: isi bisa diganti, jumlah pembacaan dihitung, bisa dibuat error. */
    private static final class FakeStore implements AiLimitStore {
        Map<String, Integer> rows = new HashMap<>();
        final AtomicInteger reads = new AtomicInteger();
        boolean failing;

        @Override
        public Map<String, Integer> overrides() {
            reads.incrementAndGet();
            if (failing) throw new IllegalStateException("db mati");
            return new HashMap<>(rows);
        }
    }

    private AiLimitProvider provider(FakeStore store) {
        return new AiLimitProvider(props, store, clock);
    }

    @Test
    void withoutOverridesEveryTierEqualsTheConfiguredDefaults() {
        AiLimitProvider p = AiLimitProvider.defaultsOnly(props);

        assertEquals(props.getFree().getDailyLimit(), p.tier(AiTier.FREE).getDailyLimit());
        assertEquals(props.getVip().getDailyLimit(), p.tier(AiTier.VIP_MONTHLY).getDailyLimit());
        assertEquals(props.getVip().getDailyLimit(), p.tier(AiTier.VIP_YEARLY).getDailyLimit());
        assertEquals(props.getGlobalDailyLimit(), p.globalDailyLimit());
        assertEquals(props.getQuotaCircuitMinutes(), p.quotaCircuitMinutes());
    }

    @Test
    void legacyVipRowsAreReadAsVipMonthly() {
        FakeStore store = new FakeStore();
        store.rows.put("VIP_MONTHLY.TESTCASE_MAX_DRAFTS", 9);
        AiLimitProvider p = provider(store);

        assertEquals(9, p.tier(AiTier.VIP).getTestcaseMaxDrafts());
        assertSame(p.tier(AiTier.VIP_MONTHLY), p.tier(AiTier.VIP));
        assertEquals(AiTier.VIP_MONTHLY, AiTier.VIP.canonical());
        assertEquals("VIP", AiTier.VIP_YEARLY.group());
        assertEquals("FREE", AiTier.FREE.group());
    }

    @Test
    void overrideAppliesOnlyToItsOwnTierAndNeverMutatesTheConfiguration() {
        FakeStore store = new FakeStore();
        store.rows.put("VIP_YEARLY.AUTOMATION_DAILY_LIMIT", 100);
        store.rows.put("FREE.TESTCASE_DAILY_LIMIT", 1);
        AiLimitProvider p = provider(store);

        assertEquals(100, p.tier(AiTier.VIP_YEARLY).getDailyLimit());
        assertEquals(props.getVip().getDailyLimit(), p.tier(AiTier.VIP_MONTHLY).getDailyLimit());
        assertEquals(props.getFree().getDailyLimit(), p.tier(AiTier.FREE).getDailyLimit());
        assertEquals(1, p.tier(AiTier.FREE).getTestcaseDailyLimit());
        // objek konfigurasi asli tidak tersentuh, jadi "nilai bawaan" tetap bisa dikembalikan
        assertEquals(30, props.getVip().getDailyLimit());
        assertEquals(30, p.defaultValue("VIP_YEARLY", AiLimitKey.AUTOMATION_DAILY_LIMIT));
        assertEquals(100, p.value("VIP_YEARLY", AiLimitKey.AUTOMATION_DAILY_LIMIT));
    }

    @Test
    void globalOverridesApply() {
        FakeStore store = new FakeStore();
        store.rows.put("GLOBAL.GLOBAL_DAILY_LIMIT", 250);
        store.rows.put("GLOBAL.QUOTA_CIRCUIT_MINUTES", 45);
        AiLimitProvider p = provider(store);

        assertEquals(250, p.globalDailyLimit());
        assertEquals(45, p.quotaCircuitMinutes());
        assertEquals(250, p.value(AiLimitKey.GLOBAL_SCOPE, AiLimitKey.GLOBAL_DAILY_LIMIT));
    }

    @Test
    void outOfRangeOrUnknownRowsAreIgnored() {
        FakeStore store = new FakeStore();
        store.rows.put("FREE.AUTOMATION_TIMEOUT_SECONDS", 5);       // di bawah min 10
        store.rows.put("FREE.AUTOMATION_MAX_TEST_CASES", 0);        // min 1
        store.rows.put("FREE.TIDAK_ADA", 7);
        store.rows.put("GLOBAL.QUOTA_CIRCUIT_MINUTES", 99_999);     // di atas max
        AiLimitProvider p = provider(store);

        assertEquals(props.getFree().getTimeoutSeconds(), p.tier(AiTier.FREE).getTimeoutSeconds());
        assertEquals(props.getFree().getMaxTestCases(), p.tier(AiTier.FREE).getMaxTestCases());
        assertEquals(props.getQuotaCircuitMinutes(), p.quotaCircuitMinutes());
    }

    @Test
    void valuesAreCachedUntilTheTtlOrInvalidate() {
        FakeStore store = new FakeStore();
        AiLimitProvider p = provider(store);

        p.tier(AiTier.FREE);
        p.tier(AiTier.VIP_YEARLY);
        p.globalDailyLimit();
        assertEquals(1, store.reads.get(), "satu pembacaan untuk banyak panggilan");

        store.rows.put("FREE.AUTOMATION_DAILY_LIMIT", 8);
        assertEquals(3, p.tier(AiTier.FREE).getDailyLimit(), "masih cache");

        clock.advanceHours(1); // lewat TTL
        assertEquals(8, p.tier(AiTier.FREE).getDailyLimit());
        assertEquals(2, store.reads.get());

        store.rows.put("FREE.AUTOMATION_DAILY_LIMIT", 9);
        p.invalidate();
        assertEquals(9, p.tier(AiTier.FREE).getDailyLimit(), "invalidate langsung membaca ulang");
    }

    @Test
    void databaseFailureNeverBreaksGeneration() {
        FakeStore store = new FakeStore();
        store.failing = true;
        AiLimitProvider p = provider(store);
        assertEquals(props.getFree().getDailyLimit(), p.tier(AiTier.FREE).getDailyLimit(), "gagal sejak awal -> bawaan");

        store.failing = false;
        store.rows.put("FREE.AUTOMATION_DAILY_LIMIT", 8);
        clock.advanceHours(1);
        assertEquals(8, p.tier(AiTier.FREE).getDailyLimit());

        store.failing = true;
        store.rows.clear();
        clock.advanceHours(1);
        assertEquals(8, p.tier(AiTier.FREE).getDailyLimit(), "gagal kemudian -> nilai terakhir yang baik");
    }

    @Test
    void gatewayAndGlobalBudgetFollowTheDatabaseValues() {
        FakeStore store = new FakeStore();
        store.rows.put("VIP_YEARLY.AUTOMATION_MAX_TEST_CASES", 77);
        store.rows.put("GLOBAL.GLOBAL_DAILY_LIMIT", 5);
        AiLimitProvider p = provider(store);

        AiGateway gateway = new AiGateway(props, List.of(), p, clock);
        assertEquals(77, gateway.tier(AiTier.VIP_YEARLY).getMaxTestCases());
        assertEquals(props.getVip().getMaxTestCases(), gateway.tier(AiTier.VIP_MONTHLY).getMaxTestCases());

        AiGlobalBudget budget = new AiGlobalBudget(p, List.of(since -> 5L), clock);
        assertTrue(budget.isExceeded());
        store.rows.put("GLOBAL.GLOBAL_DAILY_LIMIT", 6);
        p.invalidate();
        assertFalse(budget.isExceeded());
    }

    @Test
    void everyKeyHasAReadableWritableTierAccessorAndAConsistentStorageKey() {
        for (AiLimitKey key : AiLimitKey.tierKeys()) {
            AiTierProperties t = new AiTierProperties();
            key.write(t, key.min() == 0 ? 1 : key.min());
            assertEquals(key.min() == 0 ? 1 : key.min(), key.read(t), key.name());
            assertEquals(key, AiLimitKey.fromStorageKey(key.storageKey("VIP_YEARLY")).orElseThrow());
        }
        assertEquals(10, AiLimitKey.values().length);
        assertTrue(AiLimitKey.fromStorageKey("GLOBAL.AUTOMATION_DAILY_LIMIT").isEmpty(), "kunci tier tidak boleh bercakupan GLOBAL");
        assertTrue(AiLimitKey.fromStorageKey("FREE.GLOBAL_DAILY_LIMIT").isEmpty());
        assertTrue(AiLimitKey.fromStorageKey("VIP.AUTOMATION_DAILY_LIMIT").isEmpty(), "VIP lama bukan cakupan penyimpanan");
        assertTrue(AiLimitKey.fromStorageKey(null).isEmpty());
        assertTrue(AiLimitKey.fromStorageKey("tanpa-titik").isEmpty());
    }
}
