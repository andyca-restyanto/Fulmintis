// filepath: /backend/src/main/java/com/example/app/shared/ai/AiTierProperties.java
package com.example.app.shared.ai;

import lombok.Getter;
import lombok.Setter;

/**
 * Konfigurasi satu tier (app.ai.free.* / app.ai.vip.*). Nilai bawaan di bawah
 * HANYA untuk dev/uji dengan provider "fake" -- angka produksi ditentukan saat
 * provider sungguhan tersedia (model, batas token, harga per token).
 */
@Getter
@Setter
public class AiTierProperties {

    /** Nama model di provider. Wajib diisi untuk provider sungguhan. */
    private String model = "";

    /** Maks. test case yang boleh digenerate dalam satu permintaan. */
    private int maxTestCases = 5;

    /** Maks. generate per user per hari (0 = tanpa batas). */
    private int dailyLimit = 3;

    private int maxOutputTokens = 4096;

    private int timeoutSeconds = 120;

    // ---- Generate TEST CASE dgn AI (kuota & batas TERPISAH dari Automation di atas) ----

    /** Maks. generate test case per user per hari (0 = tanpa batas). Failed karena sisi AI tidak dihitung. */
    private int testcaseDailyLimit = 3;

    /** Maks. draft test case per generate. */
    private int testcaseMaxDrafts = 3;

    private int testcaseMaxOutputTokens = 4096;

    private int testcaseTimeoutSeconds = 90;
}
