// filepath: /backend/src/main/java/com/example/app/shared/ai/AiMessages.java
package com.example.app.shared.ai;

/**
 * Kode & pesan error AI yang tampil ke user, dipakai bersama oleh semua fitur AI. Pesan AI_UNAVAILABLE SENGAJA umum
 * (tidak menyebut kuota/billing/provider) dan HARUS sama persis dengan pesan fitur Automation (dijaga oleh test).
 */
public final class AiMessages {

    private AiMessages() {
    }

    public static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String AI_UNAVAILABLE_MESSAGE = "Layanan AI sedang tidak tersedia. Silakan coba lagi nanti.";

    public static final String AI_INVALID_OUTPUT = "AI_INVALID_OUTPUT";
    public static final String AI_INVALID_OUTPUT_MESSAGE = "Hasil generate tidak dapat diproses. Silakan coba lagi.";

    public static final String GENERATION_FAILED = "GENERATION_FAILED";
    public static final String GENERATION_FAILED_MESSAGE = "Terjadi kesalahan saat generate. Silakan coba lagi.";

    public static final String GENERATION_INTERRUPTED = "GENERATION_INTERRUPTED";
    public static final String GENERATION_INTERRUPTED_MESSAGE =
            "Proses generate terhenti karena server dimulai ulang. Silakan coba lagi.";

    public static final String SERVER_BUSY = "SERVER_BUSY";
    public static final String SERVER_BUSY_MESSAGE =
            "Server sedang memproses banyak permintaan. Silakan coba lagi sebentar lagi.";

    public static final String PLAN_LIMIT = "AI_PLAN_LIMIT_REACHED";
    public static final String IN_PROGRESS = "GENERATION_IN_PROGRESS";
}
