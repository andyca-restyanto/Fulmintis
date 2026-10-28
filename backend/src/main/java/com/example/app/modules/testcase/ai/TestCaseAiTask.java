// filepath: /backend/src/main/java/com/example/app/modules/testcase/ai/TestCaseAiTask.java
package com.example.app.modules.testcase.ai;

/** Tugas yang dikirim ke AI (di dalam blok data ber-escape). Semua isinya DATA, bukan perintah. */
public record TestCaseAiTask(String requirement, int count, boolean includeNegative) {
}
