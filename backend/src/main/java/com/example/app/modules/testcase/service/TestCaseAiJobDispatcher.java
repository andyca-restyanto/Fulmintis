// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseAiJobDispatcher.java
package com.example.app.modules.testcase.service;

import java.util.UUID;

/** Menjalankan job generate test case di latar belakang. Diganti implementasi sinkron di test. */
public interface TestCaseAiJobDispatcher {

    /**
     * Dijadwalkan SETELAH transaksi pemanggil commit (kalau ada). {@code requirement} diteruskan lewat MEMORI ke thread pemroses dan
     * tidak pernah ditulis ke database.
     */
    void dispatch(UUID generationId, String requirement);
}
