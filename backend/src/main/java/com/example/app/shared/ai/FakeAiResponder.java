// filepath: /backend/src/main/java/com/example/app/shared/ai/FakeAiResponder.java
package com.example.app.shared.ai;

/**
 * Pembuat keluaran palsu utk SATU fitur (dipakai FakeAiClient ketika app.ai.provider=fake). Dengan ini klien palsu tidak perlu
 * mengenal format prompt semua fitur: tiap fitur mendaftarkan responder-nya sendiri.
 */
public interface FakeAiResponder {

    /** Apakah prompt ini milik fitur saya? */
    boolean supports(AiRequest request);

    /**
     * @param truncate true = potong keluaran di tengah JSON (mensimulasikan keluaran yang terpotong batas token)
     * @return teks keluaran JSON (tanpa pembungkus)
     */
    String respond(AiRequest request, boolean truncate);
}
