// filepath: /backend/src/main/java/com/example/app/shared/ai/AiLimitStore.java
package com.example.app.shared.ai;

import java.util.Map;

/** Sumber override batas AI (diimplementasikan modul admin di atas tabel ai_limit_setting). */
public interface AiLimitStore {

    /** Kunci penyimpanan ("FREE.AUTOMATION_DAILY_LIMIT") -> nilai. Hanya baris yang ada di database. */
    Map<String, Integer> overrides();
}
