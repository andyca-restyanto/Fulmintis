// backend/src/main/java/com/example/app/modules/project/controller/ProjectCollaboratorController.java
package com.example.app.modules.project.controller;

import com.example.app.modules.project.dto.AddProjectCollaboratorRequestDTO;
import com.example.app.modules.project.dto.ProjectCollaboratorResponseDTO;
import com.example.app.modules.project.dto.UpdateProjectCollaboratorRequestDTO;
import com.example.app.modules.project.dto.UserSearchResultDTO;
import com.example.app.modules.project.service.ProjectCollaboratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * PROTECTED (wajib JWT valid) -- nested di bawah /api/projects/{projectId},
 * tidak di-permitAll() di SecurityConfig jadi otomatis kena
 * anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/projects/{projectId}/collaborators")
@RequiredArgsConstructor
public class ProjectCollaboratorController {

    private final ProjectCollaboratorService projectCollaboratorService;

    /**
     * Daftar semua team member (OWNER & COLLABORATOR) project ini. Boleh
     * dilihat member manapun (OWNER maupun COLLABORATOR), bukan owner-only.
     */
    @GetMapping
    public List<ProjectCollaboratorResponseDTO> listCollaborators(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return projectCollaboratorService.listCollaborators(email, projectId);
    }

    /**
     * Cari user untuk ditambahkan sebagai member: HANYA cocok PERSIS dengan
     * email dan HANYA user verified (balas paling banyak 1 hasil). HANYA
     * OWNER project ini yang boleh -- 403 kalau COLLABORATOR, 404 kalau bukan
     * member. Parameter {@code email} adalah yang baru; {@code keyword}
     * dipertahankan sebagai alias supaya FE lama tidak putus (perilakunya
     * sama: email utuh, bukan pencarian sebagian).
     */
    @GetMapping("/search")
    public List<UserSearchResultDTO> searchUsersToAdd(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String keyword,
            Authentication authentication
    ) {
        String callerEmail = authentication.getName();
        return projectCollaboratorService.searchUsersToAdd(callerEmail, projectId, email != null ? email : keyword);
    }

    /**
     * Tambah team member baru sebagai OWNER atau COLLABORATOR (requirement
     * #2). HANYA OWNER project ini yang boleh (requirement #3).
     * <p>
     * Balas 404 kalau project tidak ada/pemanggil bukan member, ATAU email
     * yang mau ditambahkan belum terdaftar; 403 kalau pemanggil bukan
     * OWNER; 400 kalau projectTeam bukan OWNER/COLLABORATOR; 409 kalau
     * user itu sudah jadi member project ini.
     */
    @PostMapping
    public ResponseEntity<ProjectCollaboratorResponseDTO> addCollaborator(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectCollaboratorRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        ProjectCollaboratorResponseDTO response = projectCollaboratorService.addCollaborator(email, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Ubah role member (OWNER-only). {@code collaborationId} = id baris di
     * response list collaborators. 404 kalau id bukan milik project ini; 409
     * kalau ini akan menghilangkan OWNER terakhir.
     */
    @PatchMapping("/{collaborationId}")
    public ProjectCollaboratorResponseDTO updateCollaboratorRole(
            @PathVariable UUID projectId,
            @PathVariable Long collaborationId,
            @Valid @RequestBody UpdateProjectCollaboratorRequestDTO request,
            Authentication authentication
    ) {
        return projectCollaboratorService.updateCollaboratorRole(
                authentication.getName(), projectId, collaborationId, request);
    }

    /** Hapus member (OWNER-only). 204 kalau sukses; 404/409 sama seperti PATCH. */
    @DeleteMapping("/{collaborationId}")
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable UUID projectId,
            @PathVariable Long collaborationId,
            Authentication authentication
    ) {
        projectCollaboratorService.removeCollaborator(authentication.getName(), projectId, collaborationId);
        return ResponseEntity.noContent().build();
    }
}
