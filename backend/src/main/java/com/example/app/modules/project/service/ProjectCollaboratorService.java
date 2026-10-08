// backend/src/main/java/com/example/app/modules/project/service/ProjectCollaboratorService.java
package com.example.app.modules.project.service;

import com.example.app.modules.project.dto.AddProjectCollaboratorRequestDTO;
import com.example.app.modules.project.dto.ProjectCollaboratorResponseDTO;
import com.example.app.modules.project.dto.UpdateProjectCollaboratorRequestDTO;
import com.example.app.modules.project.dto.UserSearchResultDTO;

import java.util.List;
import java.util.UUID;

public interface ProjectCollaboratorService {

    /**
     * Daftar SEMUA team member (OWNER & COLLABORATOR) di 1 project. Boleh
     * dilihat siapa saja yang jadi member project ini (bukan owner-only).
     */
    List<ProjectCollaboratorResponseDTO> listCollaborators(String userEmail, UUID projectId);

    /**
     * Cari user terdaftar (by email ATAU name, requirement #4) utk
     * ditambahkan jadi team member -- user yang SUDAH jadi member project
     * ini otomatis di-exclude dari hasil. HANYA OWNER project yang boleh
     * cari (requirement #3).
     *
     * @return list kosong kalau keyword kosong/terlalu pendek (< 2 karakter).
     */
    List<UserSearchResultDTO> searchUsersToAdd(String userEmail, UUID projectId, String keyword);

    /**
     * Tambah team member baru ke project (requirement #2), sebagai OWNER
     * atau COLLABORATOR. HANYA OWNER project yang boleh melakukan ini
     * (requirement #3).
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / pemanggil bukan member (404).
     * @throws com.example.app.modules.project.exception.ProjectAccessForbiddenException
     *         kalau pemanggil member tapi BUKAN OWNER (403).
     * @throws com.example.app.modules.project.exception.InvalidProjectTeamException
     *         kalau projectTeam yang dikirim bukan "OWNER"/"COLLABORATOR" (400).
     * @throws com.example.app.modules.project.exception.CollaboratorNotFoundException
     *         kalau email yang mau ditambahkan belum terdaftar (404).
     * @throws com.example.app.modules.project.exception.CollaboratorAlreadyExistsException
     *         kalau user itu sudah jadi member project ini (409).
     */
    ProjectCollaboratorResponseDTO addCollaborator(String userEmail, UUID projectId, AddProjectCollaboratorRequestDTO request);

    /** OWNER-only. Ubah role member. 409 kalau ini akan menghilangkan OWNER terakhir. */
    ProjectCollaboratorResponseDTO updateCollaboratorRole(
            String userEmail, UUID projectId, Long collaborationId, UpdateProjectCollaboratorRequestDTO request);

    /** OWNER-only. Hapus member dari project. 409 kalau ini akan menghilangkan OWNER terakhir. */
    void removeCollaborator(String userEmail, UUID projectId, Long collaborationId);
}
