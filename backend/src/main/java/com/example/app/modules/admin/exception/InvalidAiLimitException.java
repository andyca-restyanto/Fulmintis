// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/InvalidAiLimitException.java
package com.example.app.modules.admin.exception;

/** Nilai/kunci batas AI tidak valid (di luar rentang, kosong, tanpa konfirmasi "tanpa batas") -> 400. */
public class InvalidAiLimitException extends RuntimeException {
    public InvalidAiLimitException(String message) {
        super(message);
    }
}
