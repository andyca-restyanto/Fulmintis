// filepath: /backend/src/main/java/com/example/app/shared/ai/AiRequest.java
package com.example.app.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;

/**
 * Permintaan generik ke penyedia AI -- tidak mengandung apa pun yang spesifik provider.
 *
 * @param jsonOutput     true = minta keluaran berupa JSON (mis. responseMimeType application/json pada Gemini)
 * @param responseSchema JSON Schema standar (huruf kecil) utk membatasi bentuk keluaran, atau null. Adapter yang
 *                       menerjemahkannya ke format provider (dan boleh mengabaikannya bila belum didukung).
 */
public record AiRequest(
        String systemPrompt,
        String userPrompt,
        String model,
        int maxOutputTokens,
        Duration timeout,
        boolean jsonOutput,
        JsonNode responseSchema
) {

    /** Bentuk lama (tanpa mode JSON): tetap dipakai fitur Automation dan test. */
    public AiRequest(String systemPrompt, String userPrompt, String model, int maxOutputTokens, Duration timeout) {
        this(systemPrompt, userPrompt, model, maxOutputTokens, timeout, false, null);
    }
}
