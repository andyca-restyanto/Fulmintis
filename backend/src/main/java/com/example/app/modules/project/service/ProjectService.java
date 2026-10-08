// backend/src/main/java/com/example/app/modules/project/service/ProjectService.java
package com.example.app.modules.project.service;

import com.example.app.modules.project.dto.CreateProjectRequestDTO;
import com.example.app.modules.project.dto.ProjectDetailResponseDTO;
import com.example.app.modules.project.dto.ProjectListItemResponseDTO;
import com.example.app.modules.project.dto.ProjectResponseDTO;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    /**
     * Buat project baru. User yang membuat (dari JWT/Authentication) otomatis
     * jadi OWNER lewat entry baru di project_collaboration.
     */
    ProjectResponseDTO createProject(String userEmail, CreateProjectRequestDTO request);

    /**
     * Daftar semua project yang user ini jadi member-nya (OWNER maupun
     * COLLABORATOR), diurutkan dari yang paling baru dibuat. Dipakai
     * frontend utk render daftar project sebelum user klik salah satu.
     */
    List<ProjectListItemResponseDTO> listMyProjects(String userEmail);

    /**
     * Detail 1 project -- dipanggil saat user KLIK project dari daftar.
     * Sekaligus mengembalikan menu navigasi (Dashboard/Test Repository/
     * Test Runs/Report/Setting) yang boleh diakses user ini di project ini.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada ATAU user bukan member project ini.
     */
    ProjectDetailResponseDTO getProjectDetail(String userEmail, UUID projectId);
}
