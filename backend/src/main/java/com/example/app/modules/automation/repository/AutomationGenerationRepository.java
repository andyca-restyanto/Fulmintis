// filepath: /backend/src/main/java/com/example/app/modules/automation/repository/AutomationGenerationRepository.java
package com.example.app.modules.automation.repository;

import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.entity.AutomationGeneration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationGenerationRepository extends JpaRepository<AutomationGeneration, UUID> {

    Optional<AutomationGeneration> findByIdAndProjectId(UUID id, UUID projectId);

    List<AutomationGeneration> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    boolean existsByRequestedByAndStatusIn(String requestedBy, Collection<AutomationGenerationStatus> statuses);

    // Kuota harian per user.
    long countByRequestedByAndStatusInAndCreatedAtGreaterThanEqual(
            String requestedBy, Collection<AutomationGenerationStatus> statuses, LocalDateTime since);

    // Pengaman biaya seluruh sistem.
    long countByStatusInAndCreatedAtGreaterThanEqual(Collection<AutomationGenerationStatus> statuses, LocalDateTime since);

    /**
     * Dipanggil saat startup: job yang masih QUEUED/RUNNING pasti sudah mati bersama proses
     * sebelumnya (thread pool ada di memori) -- tandai gagal supaya user tidak menunggu selamanya
     * dan tidak terblokir aturan "satu job aktif per user".
     */
    @Transactional
    @Modifying
    @Query("""
            update AutomationGeneration g
               set g.status = :failed, g.errorCode = :code, g.errorMessage = :message, g.finishedAt = :now
             where g.status in :active
            """)
    int failActiveJobs(@Param("active") Collection<AutomationGenerationStatus> active,
                       @Param("failed") AutomationGenerationStatus failed,
                       @Param("code") String code,
                       @Param("message") String message,
                       @Param("now") LocalDateTime now);
}
