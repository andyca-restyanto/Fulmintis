// filepath: /backend/src/main/java/com/example/app/shared/ai/AiResponse.java
package com.example.app.shared.ai;

/** Hasil generik dari penyedia AI: teks + pemakaian token (0 kalau provider tidak melaporkannya). */
public record AiResponse(String text, int inputTokens, int outputTokens, String model) {
}
