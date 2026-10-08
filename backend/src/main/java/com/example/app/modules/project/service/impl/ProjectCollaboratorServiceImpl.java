// filepath: /backend/src/main/java/com/example/app/modules/project/service/impl/ProjectCollaboratorServiceImpl.java
package com.example.app.modules.project.service.impl;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.project.dto.AddProjectCollaboratorRequestDTO;
import com.example.app.modules.project.dto.ProjectCollaboratorResponseDTO;
import com.example.app.modules.project.dto.UpdateProjectCollaboratorRequestDTO;
import com.example.app.modules.project.dto.UserSearchResultDTO;
import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.exception.CollaborationNotFoundException;
import com.example.app.modules.project.exception.CollaboratorAlreadyExistsException;
import com.example.app.modules.project.exception.CollaboratorNotFoundException;
import com.example.app.modules.project.exception.InvalidProjectTeamException;
import com.example.app.modules.project.exception.LastOwnerException;
import com.example.app.modules.project.repository.ProjectCollaborationRepository;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.project.service.ProjectCollaboratorService;
import com.example.app.modules.projectteam.ProjectTeamCode;
import com.example.app.modules.projectteam.repository.ProjectTeamRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectCollaboratorServiceImpl implements ProjectCollaboratorService {

    private static final String OWNER_ONLY_MESSAGE =
            "Hanya OWNER project yang bisa menambahkan atau mencari team member.";
    private static final String OWNER_ONLY_MANAGE_MESSAGE =
            "Hanya OWNER project yang bisa mengubah atau menghapus team member.";

    private final ProjectCollaborationRepository projectCollaborationRepository; // -> datasource "frontline"
    private final UserRepository userRepository;                                 // -> datasource "frontline"
    private final ProjectTeamRepository projectTeamRepository;                   // -> datasource "master_data"
    private final ProjectAccessService projectAccessService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectCollaboratorResponseDTO> listCollaborators(String userEmail, UUID projectId) {
        // Siapa saja yang jadi member project ini (OWNER maupun COLLABORATOR)
        // boleh lihat daftar team-nya -- beda dengan add/search/ubah/hapus.
        projectAccessService.requireMember(userEmail, projectId);

        return projectCollaborationRepository.findByProjectId(projectId).stream()
                .map(this::toCollaboratorDTO)
                .toList();
    }

    /**
     * Cari user untuk ditambahkan: HANYA cocok PERSIS dengan email dan HANYA
     * user yang sudah verified. Balas paling banyak 1 hasil (atau kosong kalau
     * tidak ada / sudah jadi member). Sebelumnya pencarian substring email/nama
     * atas SEMUA user -- bisa dipakai siapa pun (cukup bikin project sendiri
     * jadi OWNER) untuk memanen daftar email user aplikasi.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResultDTO> searchUsersToAdd(String userEmail, UUID projectId, String keyword) {
        projectAccessService.requireOwner(userEmail, projectId, OWNER_ONLY_MESSAGE);

        if (keyword == null) {
            return List.of();
        }
        String email = keyword.trim().toLowerCase();
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return List.of(); // bukan email utuh -> tidak dicari sama sekali
        }

        Optional<User> match = userRepository.findByEmailAndVerifiedTrue(email);
        if (match.isEmpty()) {
            return List.of();
        }

        User candidate = match.get();
        boolean alreadyMember = projectCollaborationRepository
                .findByProjectIdAndUserId(projectId, candidate.getId())
                .isPresent();
        if (alreadyMember) {
            return List.of();
        }

        return List.of(UserSearchResultDTO.builder()
                .id(candidate.getId())
                .email(candidate.getEmail())
                .name(candidate.getName())
                .build());
    }

    @Override
    @Transactional
    public ProjectCollaboratorResponseDTO addCollaborator(
            String userEmail, UUID projectId, AddProjectCollaboratorRequestDTO request
    ) {
        ProjectCollaboration myCollaboration =
                projectAccessService.requireOwner(userEmail, projectId, OWNER_ONLY_MESSAGE);
        Project project = myCollaboration.getProject();

        String normalizedRole = validRoleOrThrow(request.getProjectTeam());

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        // Hanya user verified -- akun yang belum verifikasi email tidak bisa
        // ditambahkan (dan dibalas sama dengan "tidak terdaftar").
        User targetUser = userRepository.findByEmailAndVerifiedTrue(normalizedEmail)
                .orElseThrow(CollaboratorNotFoundException::new);

        boolean alreadyMember = projectCollaborationRepository
                .findByProjectIdAndUserId(projectId, targetUser.getId())
                .isPresent();
        if (alreadyMember) {
            throw new CollaboratorAlreadyExistsException();
        }

        ProjectCollaboration newCollaboration = ProjectCollaboration.builder()
                .project(project)
                .user(targetUser)
                .projectTeam(normalizedRole)
                .createdBy(userEmail)
                .updatedBy(userEmail)
                .build();

        ProjectCollaboration savedCollaboration = projectCollaborationRepository.save(newCollaboration);

        activityLogService.log(userEmail, "ADD_PROJECT_COLLABORATOR");

        return toCollaboratorDTO(savedCollaboration);
    }

    @Override
    @Transactional
    public ProjectCollaboratorResponseDTO updateCollaboratorRole(
            String userEmail, UUID projectId, Long collaborationId, UpdateProjectCollaboratorRequestDTO request
    ) {
        projectAccessService.requireOwner(userEmail, projectId, OWNER_ONLY_MANAGE_MESSAGE);

        ProjectCollaboration target = projectCollaborationRepository
                .findByIdAndProjectId(collaborationId, projectId)
                .orElseThrow(CollaborationNotFoundException::new);

        String newRole = validRoleOrThrow(request.getProjectTeam());

        // Idempotent: role sama -> tidak ada yang berubah.
        if (newRole.equals(target.getProjectTeam())) {
            return toCollaboratorDTO(target);
        }

        // Menurunkan OWNER terakhir = project tanpa pemilik -> ditolak.
        if (ProjectTeamCode.OWNER.equals(target.getProjectTeam())) {
            requireAnotherOwnerExists(projectId);
        }

        target.setProjectTeam(newRole);
        target.setUpdatedBy(userEmail);
        ProjectCollaboration saved = projectCollaborationRepository.save(target);

        activityLogService.log(userEmail, "UPDATE_PROJECT_COLLABORATOR_ROLE");

        return toCollaboratorDTO(saved);
    }

    @Override
    @Transactional
    public void removeCollaborator(String userEmail, UUID projectId, Long collaborationId) {
        projectAccessService.requireOwner(userEmail, projectId, OWNER_ONLY_MANAGE_MESSAGE);

        ProjectCollaboration target = projectCollaborationRepository
                .findByIdAndProjectId(collaborationId, projectId)
                .orElseThrow(CollaborationNotFoundException::new);

        // Menghapus OWNER terakhir (termasuk diri sendiri) -> ditolak.
        if (ProjectTeamCode.OWNER.equals(target.getProjectTeam())) {
            requireAnotherOwnerExists(projectId);
        }

        projectCollaborationRepository.delete(target);

        activityLogService.log(userEmail, "REMOVE_PROJECT_COLLABORATOR");
    }

    /** Role harus ada di tabel project_team (master_data); balas nama role ter-normalisasi. */
    private String validRoleOrThrow(String rawRole) {
        String normalizedRole = rawRole.trim().toUpperCase();
        if (!projectTeamRepository.existsByDescription(normalizedRole)) {
            throw new InvalidProjectTeamException(normalizedRole);
        }
        return normalizedRole;
    }

    /** Dipanggil SEBELUM OWNER diturunkan/dihapus: harus masih ada OWNER lain. */
    private void requireAnotherOwnerExists(UUID projectId) {
        long owners = projectCollaborationRepository.countByProjectIdAndProjectTeam(projectId, ProjectTeamCode.OWNER);
        if (owners <= 1) {
            throw new LastOwnerException();
        }
    }

    private ProjectCollaboratorResponseDTO toCollaboratorDTO(ProjectCollaboration collaboration) {
        return ProjectCollaboratorResponseDTO.builder()
                .id(collaboration.getId())
                .userId(collaboration.getUser().getId())
                .email(collaboration.getUser().getEmail())
                .name(collaboration.getUser().getName())
                .projectTeam(collaboration.getProjectTeam())
                .createdAt(collaboration.getCreatedAt())
                .build();
    }
}
