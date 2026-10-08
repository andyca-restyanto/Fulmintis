// filepath: /backend/src/main/java/com/example/app/shared/ai/AiUsageCounter.java
package com.example.app.shared.ai;

import java.time.LocalDateTime;

/**
 * Setiap fitur AI mendaftarkan SATU implementasi yang menghitung generate seluruh user sejak waktu tertentu (status yang
 * dihitung: berhasil + sedang berjalan; yang gagal tidak). {@link AiGlobalBudget} menjumlahkan semuanya, sehingga
 * app.ai.global-daily-limit menjaga anggaran LINTAS fitur tanpa modul saling mengimpor.
 */
public interface AiUsageCounter {

    long countSince(LocalDateTime since);
}
