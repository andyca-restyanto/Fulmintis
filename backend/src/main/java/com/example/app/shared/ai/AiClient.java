// filepath: /backend/src/main/java/com/example/app/shared/ai/AiClient.java
package com.example.app.shared.ai;

/**
 * Adapter ke satu penyedia AI. Untuk memasang provider baru: buat SATU kelas
 * yang mengimplementasikan antarmuka ini sebagai @Component, kembalikan
 * {@link #providerId()}-nya (nilai {@code app.ai.provider}), dan terjemahkan semua
 * kegagalan menjadi {@link AiProviderException}. Tidak ada kelas lain yang perlu diubah.
 */
public interface AiClient {

    /** Nilai app.ai.provider yang memilih adapter ini (huruf kecil, mis. "fake"). */
    String providerId();

    /**
     * @throws AiProviderException untuk SEMUA kegagalan provider (jangan melempar exception provider mentah)
     */
    AiResponse generate(AiRequest request);
}
