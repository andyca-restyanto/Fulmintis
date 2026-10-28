// backend/src/main/java/com/example/app/modules/testrepository/service/impl/TestFolderServiceImpl.java
package com.example.app.modules.testrepository.service.impl;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.exception.ProjectAccessForbiddenException;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.projectteam.ProjectTeamCode;
import com.example.app.modules.testrepository.dto.CreateTestFolderRequestDTO;
import com.example.app.modules.testrepository.dto.TestFolderResponseDTO;
import com.example.app.modules.testrepository.entity.TestFolder;
import com.example.app.modules.testrepository.exception.TestFolderNotFoundException;
import com.example.app.modules.testrepository.repository.TestFolderRepository;
import com.example.app.modules.testrepository.service.TestFolderService;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestFolderServiceImpl implements TestFolderService {

    private final TestFolderRepository testFolderRepository;                    // -> datasource "frontline"
    private final ProjectAccessService projectAccessService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public TestFolderResponseDTO createFolder(String userEmail, UUID projectId, CreateTestFolderRequestDTO request) {
        // Sama seperti ProjectServiceImpl.getProjectDetail(): 1 query ini
        // sekaligus jawab "project ada?" + "user member?" -- kosong -> 404.
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        // Requirement #3: create test suite/folder HANYA boleh OWNER project.
        if (!ProjectTeamCode.OWNER.equals(myCollaboration.getProjectTeam())) {
            throw new ProjectAccessForbiddenException(
                    "Hanya OWNER project yang bisa membuat test suite/folder."
            );
        }

        Project project = myCollaboration.getProject();

        // Requirement #2: parent folder OPSIONAL -- null = root. Kalau
        // diisi, WAJIB folder lain yang project_id-nya SAMA dengan project
        // ini (folder milik project lain tidak boleh dipakai jadi parent).
        TestFolder parent = null;
        if (request.getParentId() != null) {
            parent = testFolderRepository.findById(request.getParentId())
                    .filter(folder -> folder.getProject().getId().equals(projectId))
                    .orElseThrow(TestFolderNotFoundException::new);
        }

        TestFolder folder = TestFolder.builder()
                .folderName(request.getFolderName().trim())
                .project(project)
                .parent(parent)
                .createdBy(userEmail)
                .updatedBy(userEmail)
                .build();

        TestFolder savedFolder = testFolderRepository.save(folder);

        activityLogService.log(userEmail, "CREATE_TEST_FOLDER");

        return toResponseDTO(savedFolder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestFolderResponseDTO> listFolders(String userEmail, UUID projectId) {
        // Cukup validasi membership (OWNER maupun COLLABORATOR boleh lihat) --
        // beda dengan createFolder() yang dibatasi OWNER saja.
        projectAccessService.requireMember(userEmail, projectId);

        List<TestFolder> folders = testFolderRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        return folders.stream().map(this::toResponseDTO).toList();
    }

    private TestFolderResponseDTO toResponseDTO(TestFolder folder) {
        // folder.getParent() proxy LAZY -- .getId() aman dipanggil tanpa
        // trigger query tambahan (Hibernate sudah tahu FK-nya dari kolom
        // parent_id itu sendiri saat bikin proxy-nya), jadi tidak perlu
        // fetch join khusus di sini.
        UUID parentId = folder.getParent() != null ? folder.getParent().getId() : null;

        return TestFolderResponseDTO.builder()
                .id(folder.getId())
                .folderName(folder.getFolderName())
                .projectId(folder.getProject().getId())
                .parentId(parentId)
                .createdAt(folder.getCreatedAt())
                .createdBy(folder.getCreatedBy())
                .updatedAt(folder.getUpdatedAt())
                .updatedBy(folder.getUpdatedBy())
                .build();
    }
}
