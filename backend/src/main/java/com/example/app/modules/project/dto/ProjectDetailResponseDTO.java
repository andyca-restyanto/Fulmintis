// backend/src/main/java/com/example/app/modules/project/dto/ProjectDetailResponseDTO.java
package com.example.app.modules.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response utk GET /api/projects/{projectId} -- dipanggil saat user KLIK 1
 * project dari daftar. Berisi detail project (utk halaman "Dashboard" project
 * itu sendiri) SEKALIGUS daftar menu navigasi (Dashboard, Test Repository,
 * Test Runs, Report, Setting) yang boleh dilihat user ini di project ini --
 * lihat ProjectMenuCode & ProjectServiceImpl.resolveAvailableMenus().
 */
@Getter
@Builder
@AllArgsConstructor
public class ProjectDetailResponseDTO {
    private UUID id;
    private String projectName;
    private String description;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // Jumlah total member (OWNER + COLLABORATOR) di project ini -- data
    // ringkasan utk ditampilkan di halaman "Dashboard" project.
    private int memberCount;

    // Role user YANG SEDANG LOGIN di project ini.
    private String myProjectTeam;

    // Kode-kode menu (dari ProjectMenuCode) yang boleh diakses user ini di
    // project ini, contoh: ["DASHBOARD","TEST_REPOSITORY","TEST_RUNS","REPORT"]
    // -- "SETTING" hanya muncul kalau myProjectTeam == "OWNER".
    private List<String> availableMenus;
}
