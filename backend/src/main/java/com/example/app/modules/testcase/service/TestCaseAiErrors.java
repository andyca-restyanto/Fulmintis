// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseAiErrors.java
package com.example.app.modules.testcase.service;

import com.example.app.shared.ai.AiApiException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/** Penolakan khusus generate test case (kode & pesan di satu tempat). Yang umum ada di {@link AiApiException}. */
public final class TestCaseAiErrors {

    private TestCaseAiErrors() {
    }

    public static AiApiException invalidRequest(String message) {
        return new AiApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    public static AiApiException requirementTooLong(int max) {
        return new AiApiException(HttpStatus.BAD_REQUEST, "REQUIREMENT_TOO_LONG",
                "Requirement maksimal " + max + " karakter.");
    }

    public static AiApiException tooManyDrafts(int max) {
        return new AiApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_DRAFTS",
                "Maksimal " + max + " draft per generate untuk akun Anda.");
    }

    public static AiApiException notFound() {
        return new AiApiException(HttpStatus.NOT_FOUND, "GENERATION_NOT_FOUND", "Hasil generate tidak ditemukan.");
    }

    public static AiApiException notReady() {
        return new AiApiException(HttpStatus.CONFLICT, "GENERATION_NOT_READY", "Hasil generate belum tersedia atau proses gagal.");
    }

    public static AiApiException alreadyCommitted() {
        return new AiApiException(HttpStatus.CONFLICT, "GENERATION_ALREADY_COMMITTED",
                "Draft ini sudah disimpan sebelumnya.");
    }

    public static AiApiException draftExpired() {
        return new AiApiException(HttpStatus.CONFLICT, "DRAFT_EXPIRED",
                "Draft sudah tidak tersedia (kedaluwarsa). Silakan generate ulang.");
    }

    public static AiApiException noDraftsSelected() {
        return new AiApiException(HttpStatus.BAD_REQUEST, "NO_DRAFTS_SELECTED", "Pilih minimal satu draft untuk disimpan.");
    }

    /** Daftar kesalahan per item: tidak ada satu pun yang tersimpan. */
    public static AiApiException invalidDrafts(List<Map<String, Object>> errors) {
        return new AiApiException(HttpStatus.BAD_REQUEST, "INVALID_DRAFTS",
                "Ada draft yang tidak valid. Tidak ada test case yang disimpan.", errors);
    }
}
