// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationJobDispatcher.java
package com.example.app.modules.automation.service;

import java.util.UUID;

/** Menjalankan job generate di latar belakang. Diganti implementasi sinkron di test. */
public interface AutomationJobDispatcher {

    /** Dijadwalkan SETELAH transaksi pemanggil commit (kalau ada) supaya worker pasti menemukan barisnya. */
    void dispatch(UUID generationId);
}
