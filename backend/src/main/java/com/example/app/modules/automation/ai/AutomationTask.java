// filepath: /backend/src/main/java/com/example/app/modules/automation/ai/AutomationTask.java
package com.example.app.modules.automation.ai;

import java.util.List;

/**
 * Tugas yang dikirim ke AI, diserialisasi sebagai JSON di dalam blok &lt;task&gt;. Semua isi di sini
 * adalah DATA (termasuk teks test case dari anggota project) -- prompt sistem menginstruksikan AI
 * untuk tidak pernah menuruti perintah yang muncul di dalamnya (mitigasi prompt injection).
 */
public record AutomationTask(
        String framework,
        String language,
        String pattern,
        String structureNotes,
        List<TestCaseSpec> testCases
) {

    public record TestCaseSpec(
            String title,
            String priority,
            String type,
            String scenarioType,
            String description,
            String objective,
            String precondition,
            List<StepSpec> steps
    ) {
    }

    /** Satu langkah beserta hasil yang diharapkan (dipasangkan per indeks dari Test step / Expected results). */
    public record StepSpec(String action, String expected) {
    }
}
