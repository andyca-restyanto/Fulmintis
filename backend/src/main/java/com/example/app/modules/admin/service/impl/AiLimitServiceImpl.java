// filepath: /backend/src/main/java/com/example/app/modules/admin/service/impl/AiLimitServiceImpl.java
package com.example.app.modules.admin.service.impl;

import com.example.app.modules.admin.dto.AiLimitsDTO;
import com.example.app.modules.admin.dto.AiLimitsDTO.AutomationLimits;
import com.example.app.modules.admin.dto.AiLimitsDTO.GlobalLimits;
import com.example.app.modules.admin.dto.AiLimitsDTO.TestcaseLimits;
import com.example.app.modules.admin.dto.AiLimitsDTO.TierLimits;
import com.example.app.modules.admin.dto.AiLimitsUpdateRequestDTO;
import com.example.app.modules.admin.entity.AiLimitSetting;
import com.example.app.modules.admin.exception.InvalidAiLimitException;
import com.example.app.modules.admin.repository.AiLimitSettingRepository;
import com.example.app.modules.admin.service.AiLimitService;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.ai.AiLimitKey;
import com.example.app.shared.ai.AiLimitProvider;
import com.example.app.shared.ai.AiTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiLimitServiceImpl implements AiLimitService {

    private final AiLimitSettingRepository repository;
    private final AiLimitProvider limits;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AiLimitsDTO get() {
        limits.invalidate(); // halaman admin selalu menampilkan keadaan terbaru, bukan cache
        Set<String> overridden = repository.findAll().stream().map(AiLimitSetting::getKey).collect(Collectors.toSet());

        Map<String, String> source = new LinkedHashMap<>();
        for (AiLimitKey key : AiLimitKey.globalKeys()) {
            String k = key.storageKey(AiLimitKey.GLOBAL_SCOPE);
            source.put(k, overridden.contains(k) ? "DATABASE" : "DEFAULT");
        }
        for (AiTier tier : AiLimitKey.limitTiers()) {
            for (AiLimitKey key : AiLimitKey.tierKeys()) {
                String k = key.storageKey(tier.name());
                source.put(k, overridden.contains(k) ? "DATABASE" : "DEFAULT");
            }
        }

        Map<String, TierLimits> tiers = new LinkedHashMap<>();
        Map<String, TierLimits> defaultTiers = new LinkedHashMap<>();
        for (AiTier tier : AiLimitKey.limitTiers()) {
            tiers.put(tier.name(), tierLimits(key -> limits.value(tier.name(), key)));
            defaultTiers.put(tier.name(), tierLimits(key -> limits.defaultValue(tier.name(), key)));
        }
        return AiLimitsDTO.builder()
                .global(globalLimits(key -> limits.value(AiLimitKey.GLOBAL_SCOPE, key)))
                .tiers(tiers)
                .source(source)
                .defaults(AiLimitsDTO.AiLimitsDefaults.builder()
                        .global(globalLimits(key -> limits.defaultValue(AiLimitKey.GLOBAL_SCOPE, key)))
                        .tiers(defaultTiers)
                        .build())
                .build();
    }

    @Override
    @Transactional
    public AiLimitsDTO update(String actorEmail, AiLimitsUpdateRequestDTO request) {
        Map<String, Integer> desired = flatten(request);

        // Validasi SEMUA dulu (rentang + konfirmasi "tanpa batas"); tidak ada penyimpanan atau catatan log sebagian.
        limits.invalidate();
        Map<String, Integer> changes = new LinkedHashMap<>();
        Map<String, Integer> before = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : desired.entrySet()) {
            AiLimitKey key = AiLimitKey.fromStorageKey(e.getKey()).orElseThrow();
            int value = e.getValue();
            if (!key.accepts(value)) {
                throw new InvalidAiLimitException(label(e.getKey()) + " harus antara " + key.min() + " dan " + key.max() + ".");
            }
            int current = limits.value(e.getKey().substring(0, e.getKey().indexOf('.')), key);
            if (current == value) continue;
            if (value == 0 && key.unlimitedAtZero() && !request.isConfirmUnlimited()) {
                throw new InvalidAiLimitException(label(e.getKey())
                        + ": nilai 0 berarti TANPA BATAS (biaya AI tidak terkendali). Centang konfirmasi untuk melanjutkan.");
            }
            changes.put(e.getKey(), value);
            before.put(e.getKey(), current);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (Map.Entry<String, Integer> e : changes.entrySet()) {
            repository.save(AiLimitSetting.builder().key(e.getKey()).value(e.getValue()).updatedBy(actorEmail).updatedAt(now).build());
            activityLogService.log(actorEmail, "AI_LIMIT_CHANGED:" + e.getKey() + ":" + before.get(e.getKey()) + "->" + e.getValue());
        }
        limits.invalidate();
        return get();
    }

    @Override
    @Transactional
    public AiLimitsDTO reset(String actorEmail, List<String> keys) {
        List<String> targets;
        if (keys == null || keys.isEmpty()) {
            targets = repository.findAll().stream().map(AiLimitSetting::getKey).toList();
        } else {
            for (String k : keys) {
                if (AiLimitKey.fromStorageKey(k).isEmpty()) {
                    throw new InvalidAiLimitException("Kunci batas tidak dikenal: " + k);
                }
            }
            targets = keys;
        }
        limits.invalidate();
        for (String k : targets) {
            if (!repository.existsById(k)) continue;
            String scope = k.substring(0, k.indexOf('.'));
            int oldValue = limits.value(scope, AiLimitKey.fromStorageKey(k).orElseThrow());
            int defaultValue = limits.defaultValue(scope, AiLimitKey.fromStorageKey(k).orElseThrow());
            repository.deleteById(k);
            activityLogService.log(actorEmail, "AI_LIMIT_RESET:" + k + ":" + oldValue + "->" + defaultValue);
        }
        limits.invalidate();
        return get();
    }

    // ---------- helper ----------

    private static GlobalLimits globalLimits(java.util.function.ToIntFunction<AiLimitKey> read) {
        return GlobalLimits.builder()
                .dailyLimit(read.applyAsInt(AiLimitKey.GLOBAL_DAILY_LIMIT))
                .quotaCircuitMinutes(read.applyAsInt(AiLimitKey.QUOTA_CIRCUIT_MINUTES))
                .build();
    }

    private static TierLimits tierLimits(java.util.function.ToIntFunction<AiLimitKey> read) {
        return TierLimits.builder()
                .automation(AutomationLimits.builder()
                        .dailyLimit(read.applyAsInt(AiLimitKey.AUTOMATION_DAILY_LIMIT))
                        .maxTestCases(read.applyAsInt(AiLimitKey.AUTOMATION_MAX_TEST_CASES))
                        .maxOutputTokens(read.applyAsInt(AiLimitKey.AUTOMATION_MAX_OUTPUT_TOKENS))
                        .timeoutSeconds(read.applyAsInt(AiLimitKey.AUTOMATION_TIMEOUT_SECONDS))
                        .build())
                .testcase(TestcaseLimits.builder()
                        .dailyLimit(read.applyAsInt(AiLimitKey.TESTCASE_DAILY_LIMIT))
                        .maxDrafts(read.applyAsInt(AiLimitKey.TESTCASE_MAX_DRAFTS))
                        .maxOutputTokens(read.applyAsInt(AiLimitKey.TESTCASE_MAX_OUTPUT_TOKENS))
                        .timeoutSeconds(read.applyAsInt(AiLimitKey.TESTCASE_TIMEOUT_SECONDS))
                        .build())
                .build();
    }

    /** Request -> peta kunci penyimpanan -> nilai. Semua nilai wajib ada dan hanya 3 tier yang dikenal. */
    private static Map<String, Integer> flatten(AiLimitsUpdateRequestDTO request) {
        Map<String, Integer> out = new LinkedHashMap<>();
        GlobalLimits g = request.getGlobal();
        put(out, AiLimitKey.GLOBAL_DAILY_LIMIT, AiLimitKey.GLOBAL_SCOPE, g.getDailyLimit());
        put(out, AiLimitKey.QUOTA_CIRCUIT_MINUTES, AiLimitKey.GLOBAL_SCOPE, g.getQuotaCircuitMinutes());

        for (String name : request.getTiers().keySet()) {
            if (AiLimitKey.limitTiers().stream().noneMatch(t -> t.name().equals(name))) {
                throw new InvalidAiLimitException("Tier tidak dikenal: " + name + ". Pilih FREE, VIP_MONTHLY, atau VIP_YEARLY.");
            }
        }
        for (AiTier tier : AiLimitKey.limitTiers()) {
            TierLimits t = request.getTiers().get(tier.name());
            if (t == null || t.getAutomation() == null || t.getTestcase() == null) {
                throw new InvalidAiLimitException("Batas tier " + tier.name() + " (automation dan testcase) wajib diisi lengkap.");
            }
            String s = tier.name();
            AutomationLimits a = t.getAutomation();
            put(out, AiLimitKey.AUTOMATION_DAILY_LIMIT, s, a.getDailyLimit());
            put(out, AiLimitKey.AUTOMATION_MAX_TEST_CASES, s, a.getMaxTestCases());
            put(out, AiLimitKey.AUTOMATION_MAX_OUTPUT_TOKENS, s, a.getMaxOutputTokens());
            put(out, AiLimitKey.AUTOMATION_TIMEOUT_SECONDS, s, a.getTimeoutSeconds());
            TestcaseLimits c = t.getTestcase();
            put(out, AiLimitKey.TESTCASE_DAILY_LIMIT, s, c.getDailyLimit());
            put(out, AiLimitKey.TESTCASE_MAX_DRAFTS, s, c.getMaxDrafts());
            put(out, AiLimitKey.TESTCASE_MAX_OUTPUT_TOKENS, s, c.getMaxOutputTokens());
            put(out, AiLimitKey.TESTCASE_TIMEOUT_SECONDS, s, c.getTimeoutSeconds());
        }
        return out;
    }

    private static void put(Map<String, Integer> out, AiLimitKey key, String scope, Integer value) {
        String storage = key.storageKey(scope);
        if (value == null) {
            throw new InvalidAiLimitException(label(storage) + " wajib diisi.");
        }
        out.put(storage, value);
    }

    private static String label(String storageKey) {
        return storageKey.replace('.', ' ').replace('_', ' ').toLowerCase();
    }
}
