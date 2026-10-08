// filepath: /backend/src/main/java/com/example/app/modules/testcase/repository/TestCaseRepository.java
package com.example.app.modules.testcase.repository;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID>, JpaSpecificationExecutor<TestCase> {

    // ---- Dashboard project: semua dihitung di DB (GROUP BY), bukan memuat test case ke memori ----

    long countByProjectIdAndStatus(UUID projectId, TestCaseStatus status);

    // Judul test case aktif di sebuah folder (huruf kecil, utk menandai draft AI yang kembar -- tidak memblokir).
    @Query("select lower(tc.title) from TestCase tc where tc.folder.id = :folderId and tc.status = :status")
    List<String> findLowerTitlesByFolderIdAndStatus(@Param("folderId") UUID folderId, @Param("status") TestCaseStatus status);

    @Query("""
            select tc.scenarioType as category, count(tc) as total
            from TestCase tc
            where tc.project.id = :projectId and tc.status = :status
            group by tc.scenarioType
            """)
    List<ScenarioCount> countByScenario(@Param("projectId") UUID projectId, @Param("status") TestCaseStatus status);

    @Query("""
            select tc.priority as category, count(tc) as total
            from TestCase tc
            where tc.project.id = :projectId and tc.status = :status
            group by tc.priority
            """)
    List<PriorityCount> countByPriority(@Param("projectId") UUID projectId, @Param("status") TestCaseStatus status);

    // category bisa null (kolom scenario_type/priority nullable di data lama) -- caller mengabaikannya.
    interface ScenarioCount {
        TestCaseScenarioType getCategory();

        Long getTotal();
    }

    interface PriorityCount {
        TestCasePriority getCategory();

        Long getTotal();
    }
}
