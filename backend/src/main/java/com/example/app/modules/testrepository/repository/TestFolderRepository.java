// backend/src/main/java/com/example/app/modules/testrepository/repository/TestFolderRepository.java
package com.example.app.modules.testrepository.repository;

import com.example.app.modules.testrepository.entity.TestFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestFolderRepository extends JpaRepository<TestFolder, UUID> {
    // "ProjectId" di-resolve Spring Data ke nested property project.id
    // (sama seperti ProjectCollaborationRepository.findByProjectId).
    List<TestFolder> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
