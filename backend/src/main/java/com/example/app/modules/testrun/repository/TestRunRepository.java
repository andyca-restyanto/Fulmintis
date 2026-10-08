// backend/src/main/java/com/example/app/modules/testrun/repository/TestRunRepository.java
package com.example.app.modules.testrun.repository;

import com.example.app.modules.testrun.TestRunStatus;
import com.example.app.modules.testrun.entity.TestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {
    // "ProjectId" di-resolve Spring Data ke nested property project.id --
    // pola sama dgn TestFolderRepository.findByProjectIdOrderByCreatedAtAsc.
    // Diurut DESC (terbaru duluan) supaya test run yang baru dibuat langsung
    // kelihatan di atas listing menu Test Run.
    List<TestRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    // Dipakai modul report (tab Overview) -- total test run di 1 project &
    // breakdown per status (PENDING/RUNNING/FINISHED), langsung COUNT di DB
    // tanpa fetch seluruh baris test run.
    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, TestRunStatus status);

    // ---- Dashboard project ----
    Optional<TestRun> findFirstByProjectIdOrderByCreatedAtDesc(UUID projectId);

    // Run yang belum FINISHED. Tidak semua ini "aktif" (run yang sudah selesai
    // dieksekusi tapi berisi FAILED tidak pernah FINISHED) -- penyaringan
    // berdasarkan progres dilakukan di DashboardCalculator.
    List<TestRun> findByProjectIdAndStatusNotOrderByCreatedAtDesc(UUID projectId, TestRunStatus status);
}
