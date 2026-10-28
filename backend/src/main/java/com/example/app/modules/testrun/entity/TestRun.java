// backend/src/main/java/com/example/app/modules/testrun/entity/TestRun.java
package com.example.app.modules.testrun.entity;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.testrun.TestRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table "test_run" (requirement #4) -- 1 sesi eksekusi test case di dalam 1
 * project (menu Test Run). project & test_run SAMA-SAMA ada di database
 * "frontline", jadi FK asli (@ManyToOne) valid, pola sama dgn TestFolder/
 * TestCase -> Project.
 * <p>
 * HANYA boleh dibuat OWNER project (requirement #6) -- ditegakkan di
 * TestRunServiceImpl.createTestRun(), BUKAN di level entity.
 */
@Entity
@Table(name = "test_run", indexes = {
        @Index(name = "idx_test_run_project_id", columnList = "project_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Default PENDING saat dibuat. Transisi ke RUNNING/FINISHED otomatis
    // (bukan di-set manual lewat API) -- lihat TestRunServiceImpl.recomputeRunStatus().
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TestRunStatus status = TestRunStatus.PENDING;

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
