// backend/src/main/java/com/example/app/modules/project/service/impl/ProjectServiceImpl.java
package com.example.app.modules.project.service.impl;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.project.ProjectMenuCode;
import com.example.app.modules.project.dto.CreateProjectRequestDTO;
import com.example.app.modules.project.dto.ProjectDetailResponseDTO;
import com.example.app.modules.project.dto.ProjectListItemResponseDTO;
import com.example.app.modules.project.dto.ProjectResponseDTO;
import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.repository.ProjectCollaborationRepository;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.project.repository.ProjectRepository;
import com.example.app.modules.project.service.ProjectService;
import com.example.app.modules.projectteam.ProjectTeamCode;
import com.example.app.modules.projectteam.repository.ProjectTeamRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;                       // -> datasource "frontline"
    private final ProjectCollaborationRepository projectCollaborationRepository; // -> datasource "frontline"
    private final UserRepository userRepository;                             // -> datasource "frontline"
    private final ProjectTeamRepository projectTeamRepository;               // -> datasource "master_data"
    private final ProjectAccessService projectAccessService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public ProjectResponseDTO createProject(String userEmail, CreateProjectRequestDTO request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan email " + userEmail + " tidak ditemukan (token valid tapi user sudah terhapus?)"
                ));

        // Validasi role OWNER ada di database master_data (tabel project_team)
        // -- READ lintas database, sama seperti validasi user_type=FREE saat
        // register (lihat AuthServiceImpl).
        boolean ownerTeamExists = projectTeamRepository.existsByDescription(ProjectTeamCode.OWNER);
        if (!ownerTeamExists) {
            throw new IllegalStateException(
                    "project_team OWNER tidak ditemukan di database master_data. "
                            + "Pastikan database 'master_data' sudah dibuat dan ProjectTeamSeeder sudah jalan."
            );
        }

        // ---- 1. Simpan Project ----
        Project project = Project.builder()
                .projectName(request.getProjectName().trim())
                .description(request.getDescription())
                .createdBy(userEmail)
                .updatedBy(userEmail)
                .build();
        Project savedProject = projectRepository.save(project);

        // ---- 2. User pembuat otomatis jadi OWNER lewat project_collaboration ----
        ProjectCollaboration collaboration = ProjectCollaboration.builder()
                .project(savedProject)
                .user(user)
                .projectTeam(ProjectTeamCode.OWNER)
                .createdBy(userEmail)
                .updatedBy(userEmail)
                .build();
        projectCollaborationRepository.save(collaboration);

        activityLogService.log(userEmail, "CREATE_PROJECT");

        return ProjectResponseDTO.builder()
                .id(savedProject.getId())
                .projectName(savedProject.getProjectName())
                .description(savedProject.getDescription())
                .createdAt(savedProject.getCreatedAt())
                .ownerEmail(userEmail)
                .projectTeam(ProjectTeamCode.OWNER)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectListItemResponseDTO> listMyProjects(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan email " + userEmail + " tidak ditemukan (token valid tapi user sudah terhapus?)"
                ));

        // Semua baris project_collaboration milik user ini -> setiap baris
        // sudah bawa relasi @ManyToOne ke Project (lihat ProjectCollaboration),
        // jadi tidak perlu query Project terpisah.
        List<ProjectCollaboration> myCollaborations = projectCollaborationRepository.findByUserId(user.getId());

        return myCollaborations.stream()
                .sorted(Comparator.comparing(
                        (ProjectCollaboration c) -> c.getProject().getCreatedAt()
                ).reversed()) // project paling baru dibuat muncul paling atas
                .map(collaboration -> {
                    Project project = collaboration.getProject();
                    return ProjectListItemResponseDTO.builder()
                            .id(project.getId())
                            .projectName(project.getProjectName())
                            .description(project.getDescription())
                            .createdAt(project.getCreatedAt())
                            .myProjectTeam(collaboration.getProjectTeam())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public ProjectDetailResponseDTO getProjectDetail(String userEmail, UUID projectId) {
        // 1 query ini SEKALIGUS jawab 2 pertanyaan: apakah project_id ini ada,
        // DAN apakah user ini member-nya -- kalau kosong (baik karena project
        // tidak ada, ATAU project ada tapi user bukan member), langsung 404.
        // Lihat komentar anti-enumeration di ProjectNotFoundException.
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        Project project = myCollaboration.getProject();

        int memberCount = projectCollaborationRepository.countByProjectId(projectId);

        List<String> availableMenus = resolveAvailableMenus(myCollaboration.getProjectTeam());

        activityLogService.log(userEmail, "VIEW_PROJECT_DETAIL");

        return ProjectDetailResponseDTO.builder()
                .id(project.getId())
                .projectName(project.getProjectName())
                .description(project.getDescription())
                .createdAt(project.getCreatedAt())
                .createdBy(project.getCreatedBy())
                .updatedAt(project.getUpdatedAt())
                .updatedBy(project.getUpdatedBy())
                .memberCount(memberCount)
                .myProjectTeam(myCollaboration.getProjectTeam())
                .availableMenus(availableMenus)
                .build();
    }

    /**
     * Tentukan menu navigasi apa saja yang boleh diakses user berdasarkan
     * role-nya di project ini.
     * <p>
     * ASUMSI BISNIS (silakan sesuaikan kalau requirement sebenarnya beda):
     * DASHBOARD, TEST_REPOSITORY, TEST_RUNS, REPORT bisa diakses SEMUA
     * member (OWNER maupun COLLABORATOR) -- SETTING cuma bisa diakses OWNER,
     * karena berisi hal sensitif project (misal: hapus project, kelola
     * member). Kalau nanti perlu role lain (misal ADMIN) atau permission
     * lebih granular, ganti logic switch di bawah ini.
     */
    private List<String> resolveAvailableMenus(String projectTeam) {
        boolean isOwner = ProjectTeamCode.OWNER.equals(projectTeam);

        return Arrays.stream(ProjectMenuCode.values())
                .filter(menu -> isOwner || menu != ProjectMenuCode.SETTING)
                .map(Enum::name)
                .toList();
    }
}
