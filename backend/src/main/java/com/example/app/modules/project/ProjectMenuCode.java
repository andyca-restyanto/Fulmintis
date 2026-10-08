// backend/src/main/java/com/example/app/modules/project/ProjectMenuCode.java
package com.example.app.modules.project;

/**
 * Kode-kode menu navigasi yang muncul saat user klik masuk ke 1 project
 * (sidebar/tab di halaman project). Dikembalikan lewat
 * ProjectDetailResponseDTO.availableMenus supaya frontend (Pigay) tidak
 * hardcode menu mana yang boleh tampil -- source of truth ada di backend.
 *
 * Modul TEST_REPOSITORY, TEST_RUNS, dan REPORT sudah punya package sendiri
 * (modules/testrepository, modules/testrun, modules/report); enum ini tetap
 * jadi source of truth menu mana yang tampil, tanpa perlu diubah tiap ada
 * modul baru.
 */
public enum ProjectMenuCode {
    DASHBOARD,
    TEST_REPOSITORY,
    TEST_RUNS,
    REPORT,
    AUTOMATION,
    SETTING
}
