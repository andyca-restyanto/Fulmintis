// filepath: /backend/src/main/java/com/example/app/shared/ai/AiGeminiProperties.java
package com.example.app.shared.ai;

import lombok.Getter;
import lombok.Setter;

/**
 * Pengaturan khusus adapter Gemini (app.ai.gemini.*). Dua parameter di bawah BELUM terverifikasi terhadap API sungguhan
 * (butuh panggilan nyata dgn key), jadi bawaannya MATI: nilai yang salah bisa membuat Gemini menolak permintaan.
 */
@Getter
@Setter
public class AiGeminiProperties {

    /** Pemetaan skema keluaran: off | response-schema (format OpenAPI, tipe huruf besar) | response-json-schema (JSON Schema). */
    private String responseSchema = "off";

    /** Anggaran token thinking (null = tidak dikirim). Token thinking ditagih sbg output; 0 mematikannya di model yang mendukung. */
    private Integer thinkingBudget;

    /** Suhu sampling (null = bawaan model). */
    private Double temperature;
}
