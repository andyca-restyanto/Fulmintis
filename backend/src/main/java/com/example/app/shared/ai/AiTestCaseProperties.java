// filepath: /backend/src/main/java/com/example/app/shared/ai/AiTestCaseProperties.java
package com.example.app.shared.ai;

import lombok.Getter;
import lombok.Setter;

/** Pengaturan fitur generate test case dgn AI yang sama utk semua tier (app.ai.testcase.*). */
@Getter
@Setter
public class AiTestCaseProperties {

    /** Panjang maks teks requirement yang dikirim ke AI. */
    private int maxRequirementChars = 6000;

    /** Draft yang belum disimpan dibersihkan setelah sekian hari (data minimal: draft hanya perlu sampai direview). */
    private int draftRetentionDays = 7;
}
