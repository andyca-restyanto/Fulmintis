// filepath: /backend/src/main/java/com/example/app/shared/ai/AiConfigurationValidator.java
package com.example.app.shared.ai;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Menggagalkan START aplikasi kalau konfigurasi AI berbahaya atau setengah jadi,
 * daripada gagal diam-diam saat user menekan Generate:
 * <ul>
 *   <li>provider kosong -> OK (fitur generate nonaktif, aplikasi start normal);</li>
 *   <li>provider "fake" di profil prod -> GAGAL (user produksi tidak boleh menerima kode palsu);</li>
 *   <li>provider tanpa adapter -> GAGAL;</li>
 *   <li>provider sungguhan tanpa baseUrl/apiKey/model/batas yang valid -> GAGAL.</li>
 * </ul>
 */
@Component
public class AiConfigurationValidator implements InitializingBean {

    public static final String FAKE_PROVIDER = "fake";
    public static final String GEMINI_PROVIDER = "gemini";
    private static final List<String> RESPONSE_SCHEMA_MODES = List.of("off", "response-schema", "response-json-schema");

    private final AiProperties properties;
    private final List<AiClient> clients;
    private final Environment environment;

    /** Dipakai Spring. ObjectProvider: aman walau TIDAK ADA bean AiClient sama sekali (provider kosong). */
    @Autowired
    public AiConfigurationValidator(AiProperties properties, ObjectProvider<AiClient> clientProvider, Environment environment) {
        this(properties, clientProvider.orderedStream().toList(), environment);
    }

    public AiConfigurationValidator(AiProperties properties, List<AiClient> clients, Environment environment) {
        this.properties = properties;
        this.clients = clients;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties,
                clients.stream().map(c -> c.providerId().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()),
                environment.acceptsProfiles(Profiles.of("prod", "production")));
    }

    /** Murni (tanpa Spring) supaya mudah diuji. */
    public static void validate(AiProperties properties, Collection<String> availableProviders, boolean productionProfile) {
        if (!properties.isConfigured()) {
            return;
        }
        String provider = properties.getProvider().trim().toLowerCase(Locale.ROOT);
        List<String> problems = new ArrayList<>();

        if (FAKE_PROVIDER.equals(provider) && productionProfile) {
            throw new IllegalStateException("Konfigurasi AI tidak valid: app.ai.provider=fake tidak boleh dipakai di profil prod "
                    + "(user produksi akan menerima kode palsu). Kosongkan app.ai.provider atau isi provider sungguhan.");
        }
        if (!availableProviders.contains(provider)) {
            throw new IllegalStateException("Konfigurasi AI tidak valid: provider '" + provider + "' belum memiliki adapter. "
                    + "Adapter yang tersedia: " + availableProviders + ". Tambahkan satu kelas AiClient untuk provider ini.");
        }

        if (!FAKE_PROVIDER.equals(provider)) {
            // Gemini punya alamat bawaan; provider lain wajib menyebutkannya.
            if (!GEMINI_PROVIDER.equals(provider) && isBlank(properties.getBaseUrl())) problems.add("app.ai.base-url");
            if (isBlank(properties.getApiKey())) problems.add("app.ai.api-key (isi lewat environment variable)");
            checkTier("free", properties.getFree(), problems);
            checkTier("vip", properties.getVip(), problems);

            String billingTier = properties.getBillingTier() == null ? "" : properties.getBillingTier().trim().toLowerCase(Locale.ROOT);
            if (!billingTier.equals("free") && !billingTier.equals("paid")) {
                problems.add("app.ai.billing-tier (free atau paid)");
            }
            // Gemini free tier: Google dapat memakai prompt utk memperbaiki produknya. Data pelanggan di produksi tidak boleh lewat sana.
            // (Ini deklarasi, bukan verifikasi -- billing sebenarnya tidak bisa dibaca dari kode -- tapi memaksa pilihan yang sadar.)
            if (GEMINI_PROVIDER.equals(provider) && productionProfile && !billingTier.equals("paid")) {
                throw new IllegalStateException("Konfigurasi AI tidak valid: provider '" + provider + "' di profil prod WAJIB memakai akun "
                        + "berbayar (app.ai.billing-tier=paid). Akun free tier dapat memakai prompt user utk memperbaiki produk penyedia. "
                        + "Gunakan project dan key terpisah yang billing-nya aktif.");
            }
            if (!RESPONSE_SCHEMA_MODES.contains(properties.getGemini().getResponseSchema() == null ? "" : properties.getGemini().getResponseSchema())) {
                problems.add("app.ai.gemini.response-schema (off | response-schema | response-json-schema)");
            }
        }
        if (properties.getTestcase().getMaxRequirementChars() <= 0) problems.add("app.ai.testcase.max-requirement-chars (> 0)");
        if (properties.getTestcase().getDraftRetentionDays() <= 0) problems.add("app.ai.testcase.draft-retention-days (> 0)");
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Konfigurasi AI tidak lengkap untuk provider '" + provider
                    + "'. Perbaiki: " + String.join(", ", problems));
        }
    }

    private static void checkTier(String name, AiTierProperties tier, List<String> problems) {
        if (isBlank(tier.getModel())) problems.add("app.ai." + name + ".model");
        if (tier.getMaxTestCases() <= 0) problems.add("app.ai." + name + ".max-test-cases (> 0)");
        if (tier.getMaxOutputTokens() <= 0) problems.add("app.ai." + name + ".max-output-tokens (> 0)");
        if (tier.getTimeoutSeconds() <= 0) problems.add("app.ai." + name + ".timeout-seconds (> 0)");
        if (tier.getDailyLimit() < 0) problems.add("app.ai." + name + ".daily-limit (>= 0)");
        if (tier.getTestcaseDailyLimit() < 0) problems.add("app.ai." + name + ".testcase-daily-limit (>= 0)");
        if (tier.getTestcaseMaxDrafts() <= 0) problems.add("app.ai." + name + ".testcase-max-drafts (> 0)");
        if (tier.getTestcaseMaxOutputTokens() <= 0) problems.add("app.ai." + name + ".testcase-max-output-tokens (> 0)");
        if (tier.getTestcaseTimeoutSeconds() <= 0) problems.add("app.ai." + name + ".testcase-timeout-seconds (> 0)");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
