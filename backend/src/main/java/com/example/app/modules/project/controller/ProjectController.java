// backend/src/main/java/com/example/app/modules/project/controller/ProjectController.java
package com.example.app.modules.project.controller;

import com.example.app.modules.project.dto.CreateProjectRequestDTO;
import com.example.app.modules.project.dto.ProjectDetailResponseDTO;
import com.example.app.modules.project.dto.ProjectListItemResponseDTO;
import com.example.app.modules.project.dto.ProjectResponseDTO;
import com.example.app.modules.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * PROTECTED (wajib JWT valid, sama seperti /api/dashboard/**) -- endpoint
     * ini tidak di-permitAll() di SecurityConfig, jadi otomatis kena
     * anyRequest().authenticated().
     */
    @PostMapping
    public ResponseEntity<ProjectResponseDTO> createProject(
            @Valid @RequestBody CreateProjectRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName(); // di-set oleh JwtAuthenticationFilter dari subject token
        ProjectResponseDTO response = projectService.createProject(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Daftar project milik user login (OWNER maupun COLLABORATOR).
     * Dipakai frontend utk render daftar project sebelum user klik salah
     * satu -> prasyarat requirement "As a user when I click project...".
     */
    @GetMapping
    public List<ProjectListItemResponseDTO> listMyProjects(Authentication authentication) {
        String email = authentication.getName();
        return projectService.listMyProjects(email);
    }

    /**
     * Detail 1 project + menu navigasi (Dashboard, Test Repository, Test
     * Runs, Report, Setting) yang boleh diakses user ini di project ini.
     * Dipanggil frontend PERSIS saat user klik 1 project dari daftar.
     * <p>
     * Balas 404 (via ProjectNotFoundException, lihat GlobalExceptionHandler)
     * kalau project tidak ada ATAU user bukan member project ini -- jadi
     * user tidak bisa asal tebak UUID project orang lain utk intip datanya.
     */
    @GetMapping("/{projectId}")
    public ProjectDetailResponseDTO getProjectDetail(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return projectService.getProjectDetail(email, projectId);
    }
}
