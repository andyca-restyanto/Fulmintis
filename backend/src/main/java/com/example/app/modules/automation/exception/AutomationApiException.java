// filepath: /backend/src/main/java/com/example/app/modules/automation/exception/AutomationApiException.java
package com.example.app.modules.automation.exception;

import com.example.app.modules.automation.AutomationMessages;
import org.springframework.http.HttpStatus;

/**
 * Satu exception utk semua penolakan di modul automation: membawa status HTTP, kode
 * mesin (errorCode, dipakai FE utk membedakan kasus), dan pesan utk user.
 */
public class AutomationApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AutomationApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ---- Pabrik utk tiap kasus (satu tempat, pesan konsisten) ----

    public static AutomationApiException aiUnavailable() {
        return new AutomationApiException(HttpStatus.SERVICE_UNAVAILABLE,
                AutomationMessages.AI_UNAVAILABLE, AutomationMessages.AI_UNAVAILABLE_MESSAGE);
    }

    public static AutomationApiException setupRequired() {
        return new AutomationApiException(HttpStatus.CONFLICT, AutomationMessages.SETUP_REQUIRED,
                "Struktur automation project ini belum diatur. Atur dulu di tab Setup.");
    }

    public static AutomationApiException unsupportedCombination(String message) {
        return new AutomationApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_COMBINATION", message);
    }

    public static AutomationApiException tooManyTestCases(int max) {
        return new AutomationApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_TEST_CASES",
                "Maksimal " + max + " test case per generate untuk akun Anda.");
    }

    public static AutomationApiException invalidTestCaseSelection() {
        return new AutomationApiException(HttpStatus.BAD_REQUEST, "INVALID_TEST_CASE_SELECTION",
                "Ada test case yang tidak ditemukan di project ini atau sudah diarsipkan.");
    }

    public static AutomationApiException dailyLimitReached(int limit) {
        return new AutomationApiException(HttpStatus.TOO_MANY_REQUESTS, AutomationMessages.PLAN_LIMIT,
                "Batas generate harian Anda tercapai (" + limit + " per hari). Coba lagi besok.");
    }

    public static AutomationApiException alreadyRunning() {
        return new AutomationApiException(HttpStatus.CONFLICT, AutomationMessages.IN_PROGRESS,
                "Masih ada proses generate Anda yang berjalan. Tunggu sampai selesai.");
    }

    public static AutomationApiException generationNotFound() {
        return new AutomationApiException(HttpStatus.NOT_FOUND, "GENERATION_NOT_FOUND", "Riwayat generate tidak ditemukan.");
    }

    public static AutomationApiException generationNotReady() {
        return new AutomationApiException(HttpStatus.CONFLICT, AutomationMessages.NOT_READY,
                "Hasil generate belum tersedia atau proses gagal.");
    }
}
