// filepath: /backend/src/main/java/com/example/app/modules/testcase/repository/TestCaseAiGenerationRepository.java
package com.example.app.modules.testcase.repository;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import com.example.app.modules.testcase.entity.TestCaseAiGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseAiGenerationRepository extends JpaRepository<TestCaseAiGeneration, UUID> {

    // Draft bersifat PRIBADI: hanya pemintanya yang boleh melihat/menyimpan.
    Optional<TestCaseAiGeneration> findByIdAndProjectIdAndRequestedBy(UUID id, UUID projectId, String requestedBy);

    // Draft terakhir yang belum disimpan & masih ada (utk melanjutkan review setelah reload).
    Optional<TestCaseAiGeneration> findFirstByProjectIdAndRequestedByAndStatusAndCommittedFalseAndDraftsJsonIsNotNullOrderByCreatedAtDesc(
            UUID projectId, String requestedBy, TestCaseAiGenerationStatus status);

    boolean existsByRequestedByAndStatusIn(String requestedBy, Collection<TestCaseAiGenerationStatus> statuses);

    // Kuota harian per user.
    long countByRequestedByAndStatusInAndCreatedAtGreaterThanEqual(
            String requestedBy, Collection<TestCaseAiGenerationStatus> statuses, LocalDateTime since);

    // Anggaran global lintas fitur.
    long countByStatusInAndCreatedAtGreaterThanEqual(Collection<TestCaseAiGenerationStatus> statuses, LocalDateTime since);

    /** Saat startup: job yang masih QUEUED/RUNNING sudah mati bersama proses lama. */
    @Transactional
    @Modifying
    @Query("""
            update TestCaseAiGeneration g
               set g.status = :failed, g.errorCode = :code, g.errorMessage = :message, g.finishedAt = :now
             where g.status in :active
            """)
    int failActiveJobs(@Param("active") Collection<TestCaseAiGenerationStatus> active,
                       @Param("failed") TestCaseAiGenerationStatus failed,
                       @Param("code") String code,
                       @Param("message") String message,
                       @Param("now") LocalDateTime now);

    /**
     * "Klaim" atomik sebelum menyimpan test case: hanya SATU permintaan commit yang bisa berhasil (klik ganda / dua tab tidak
     * membuat test case ganda). Mengembalikan 1 kalau berhasil, 0 kalau sudah di-commit / kedaluwarsa / belum SUCCEEDED.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TestCaseAiGeneration g
               set g.committed = true, g.committedAt = :now, g.committedCount = :count, g.draftsJson = null
             where g.id = :id and g.committed = false and g.status = :succeeded and g.draftsJson is not null
            """)
    int claimForCommit(@Param("id") UUID id,
                       @Param("now") LocalDateTime now,
                       @Param("count") int count,
                       @Param("succeeded") TestCaseAiGenerationStatus succeeded);

    /** Pembersihan draft yang tidak pernah disimpan setelah masa simpan (data minimal). Baris tetap ada utk hitungan kuota. */
    @Transactional
    @Modifying
    @Query("""
            update TestCaseAiGeneration g
               set g.draftsJson = null
             where g.draftsJson is not null and g.committed = false and g.createdAt < :cutoff
            """)
    int clearExpiredDrafts(@Param("cutoff") LocalDateTime cutoff);
}
