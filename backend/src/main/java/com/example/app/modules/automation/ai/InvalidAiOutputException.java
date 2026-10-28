// filepath: /backend/src/main/java/com/example/app/modules/automation/ai/InvalidAiOutputException.java
package com.example.app.modules.automation.ai;

/** Keluaran AI tidak bisa dipakai (bukan JSON, struktur salah, path berbahaya, terlalu besar). Detail hanya utk log. */
public class InvalidAiOutputException extends RuntimeException {
    public InvalidAiOutputException(String internalDetail) {
        super(internalDetail);
    }
}
