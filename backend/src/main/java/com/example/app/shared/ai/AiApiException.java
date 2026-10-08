// filepath: /backend/src/main/java/com/example/app/shared/ai/AiApiException.java
package com.example.app.shared.ai;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Penolakan generik utk fitur AI (selain Automation, yang punya exception-nya sendiri dgn kode yang sama): membawa status
 * HTTP, errorCode mesin, pesan utk user, dan (opsional) daftar kesalahan per item.
 */
public class AiApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final transient List<Map<String, Object>> errors;

    public AiApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public AiApiException(HttpStatus status, String errorCode, String message, List<Map<String, Object>> errors) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.errors = errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /** Kesalahan per item (mis. per draft saat commit), atau null. */
    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public static AiApiException aiUnavailable() {
        return new AiApiException(HttpStatus.SERVICE_UNAVAILABLE, AiMessages.AI_UNAVAILABLE, AiMessages.AI_UNAVAILABLE_MESSAGE);
    }

    public static AiApiException dailyLimitReached(int limit) {
        return new AiApiException(HttpStatus.TOO_MANY_REQUESTS, AiMessages.PLAN_LIMIT,
                "Batas generate harian Anda tercapai (" + limit + " per hari). Coba lagi besok.");
    }

    public static AiApiException alreadyRunning() {
        return new AiApiException(HttpStatus.CONFLICT, AiMessages.IN_PROGRESS,
                "Masih ada proses generate Anda yang berjalan. Tunggu sampai selesai.");
    }
}
