// filepath: /backend/src/main/java/com/example/app/shared/ai/AiCallSpec.java
package com.example.app.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;

/**
 * Spesifikasi satu panggilan AI dari sebuah fitur. Model dipilih gateway dari tier user; batas keluaran dan timeout
 * ditentukan FITUR yang memanggil (generate test case punya batas sendiri, terpisah dari Automation).
 */
public record AiCallSpec(
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Duration timeout,
        boolean jsonOutput,
        JsonNode responseSchema
) {
}
