// backend/src/main/java/com/example/app/modules/project/repository/ProjectCollaborationRepository.java
package com.example.app.modules.project.repository;

import com.example.app.modules.project.entity.ProjectCollaboration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCollaborationRepository extends JpaRepository<ProjectCollaboration, Long> {
    List<ProjectCollaboration> findByUserId(UUID userId);
    List<ProjectCollaboration> findByProjectId(UUID projectId);

    // Dipakai untuk 2 hal sekaligus: (1) cek apakah user ini member dari
    // project ini atau bukan, (2) kalau iya, ambil role-nya (OWNER/COLLABORATOR)
    // -- dipakai di getProjectDetail() untuk validasi akses + tentuin menu apa
    // saja yang boleh dilihat user.
    Optional<ProjectCollaboration> findByProjectIdAndUserId(UUID projectId, UUID userId);

    int countByProjectId(UUID projectId);

    // Dipakai ubah role / hapus member: id baris HARUS milik project di URL
    // (mencegah OWNER project A mengutak-atik member project B lewat id).
    Optional<ProjectCollaboration> findByIdAndProjectId(Long id, UUID projectId);

    // Jumlah member berrole tertentu -- dipakai jaga aturan "minimal 1 OWNER".
    long countByProjectIdAndProjectTeam(UUID projectId, String projectTeam);
}
