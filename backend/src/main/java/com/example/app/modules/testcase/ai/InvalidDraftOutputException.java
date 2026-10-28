// filepath: /backend/src/main/java/com/example/app/modules/testcase/ai/InvalidDraftOutputException.java
package com.example.app.modules.testcase.ai;

/** Keluaran AI tidak menghasilkan satu draft pun yang bisa dipakai. Detail hanya utk log, tidak pernah ke user. */
public class InvalidDraftOutputException extends RuntimeException {
    public InvalidDraftOutputException(String internalDetail) {
        super(internalDetail);
    }
}
