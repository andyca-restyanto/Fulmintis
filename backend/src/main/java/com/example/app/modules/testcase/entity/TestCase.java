// backend/src/main/java/com/example/app/modules/testcase/entity/TestCase.java
package com.example.app.modules.testcase.entity;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testrepository.entity.TestFolder;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table "test_case" -- 1 test case SELALU ada di dalam 1 folder (requirement
 * #2, folder_id NOT NULL) dan 1 project. project, test_folder, test_case
 * SEMUA ada di database "frontline", jadi FK asli (@ManyToOne) valid.
 */
@Entity
@Table(name = "test_case", indexes = {
        @Index(name = "idx_test_case_project_status", columnList = "project_id, status"),
        @Index(name = "idx_test_case_folder_id", columnList = "folder_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // WAJIB diisi -- requirement #2: test case cuma bisa dibuat kalau
    // folder/test suite-nya sudah ada.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private TestFolder folder;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCasePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCaseType type;

    // Fixed-choice: POSITIVE | NEGATIVE.
    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", length = 20)
    private TestCaseScenarioType scenarioType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String objective;

    @Column(columnDefinition = "TEXT")
    private String precondition;

    @Column(name = "test_step", columnDefinition = "TEXT")
    private String testStep;

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    // Requirement tambahan #2 & #4: delete test case = soft delete (flag
    // ARCHIVED, row TIDAK dihapus dari database), dan test case ARCHIVED
    // cuma boleh dilihat OWNER project (COLLABORATOR tidak boleh lihat sama
    // sekali). Default ACTIVE supaya kompatibel dgn test case existing yang
    // sudah dibuat sebelum kolom ini ada (Hibernate ddl-auto=update -> baris
    // lama otomatis NULL, di-treat sbg ACTIVE lewat @Builder.Default utk row
    // baru; lihat migration V10 utk baris lama).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TestCaseStatus status = TestCaseStatus.ACTIVE;

    // Diisi cuma pada saat test case di-archive (delete). NULL selama
    // status = ACTIVE.
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archived_by")
    private String archivedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
