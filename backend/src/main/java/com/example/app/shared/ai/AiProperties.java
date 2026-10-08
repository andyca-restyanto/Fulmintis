// filepath: /backend/src/main/java/com/example/app/shared/ai/AiProperties.java
package com.example.app.shared.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfigurasi koneksi AI (prefix app.ai). Rahasia (apiKey) HANYA dari environment
 * variable; tidak pernah dikirim ke frontend dan tidak pernah ditulis ke log.
 * {@code provider} kosong = fitur generate nonaktif (aplikasi tetap start normal).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Kosong = nonaktif. "fake" = klien palsu (hanya dev/test; DILARANG di profil prod). */
    private String provider = "";

    private String baseUrl = "";

    private String apiKey = "";

    /** Maks. generate seluruh sistem per hari sebagai pengaman biaya (0 = tanpa batas). */
    private int globalDailyLimit = 0;

    /** Berapa menit panggilan ke provider ditahan setelah provider melaporkan kuota/saldo habis. */
    private int quotaCircuitMinutes = 10;

    /**
     * Tier akun di penyedia: "free" (mis. free tier Gemini -- penyedia dapat memakai prompt utk memperbaiki produknya) atau
     * "paid". Di profil prod, provider Gemini WAJIB "paid" (lihat AiConfigurationValidator).
     */
    private String billingTier = "free";

    private AiTestCaseProperties testcase = new AiTestCaseProperties();

    private AiGeminiProperties gemini = new AiGeminiProperties();

    private AiTierProperties free = new AiTierProperties();

    private AiTierProperties vip = vipDefaults();

    private static AiTierProperties vipDefaults() {
        AiTierProperties vip = new AiTierProperties();
        vip.setMaxTestCases(20);
        vip.setDailyLimit(30);
        vip.setMaxOutputTokens(8192);
        // Generate test case: VIP 15 generate/hari, 15 draft/generate (FREE: 3 dan 3 -- bawaan AiTierProperties).
        vip.setTestcaseDailyLimit(15);
        vip.setTestcaseMaxDrafts(15);
        vip.setTestcaseMaxOutputTokens(8192);
        vip.setTestcaseTimeoutSeconds(120);
        return vip;
    }

    public AiTierProperties tier(AiTier tier) {
        return tier == AiTier.VIP ? vip : free;
    }

    public boolean isConfigured() {
        return provider != null && !provider.isBlank();
    }

    /**
     * true = data user dikirim ke penyedia yang MUNGKIN memakainya (akun free tier). Dipakai UI utk menampilkan peringatan
     * "jangan masukkan data asli". Provider "fake" tidak mengirim data ke mana pun, jadi bukan sandbox.
     */
    public boolean isSandbox() {
        return isConfigured() && !"fake".equalsIgnoreCase(provider.trim()) && !"paid".equalsIgnoreCase(billingTier == null ? "" : billingTier.trim());
    }
}
