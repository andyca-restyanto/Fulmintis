// filepath: /backend/src/main/java/com/example/app/modules/project/service/ProjectAccessService.java
package com.example.app.modules.project.service;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.exception.ProjectAccessForbiddenException;
import com.example.app.modules.project.exception.ProjectNotFoundException;
import com.example.app.modules.projectteam.ProjectTeamCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Satu-satunya tempat aturan akses project (sebelumnya pola "cari user ->
 * cari collaboration -> 404 -> cek OWNER -> 403" ditulis ulang di 22 tempat
 * di 6 service). Aturan:
 * <ul>
 *   <li>Bukan member / project tidak ada -> 404 ({@link ProjectNotFoundException}),
 *       supaya keberadaan project tidak bocor (anti-enumeration).</li>
 *   <li>Member tapi bukan OWNER untuk aksi owner-only -> 403.</li>
 * </ul>
 * Sengaja TANPA {@code @Transactional} sendiri: dipanggil dari service yang
 * sudah {@code @Transactional}, sehingga entity {@link ProjectCollaboration}
 * yang dibalas (termasuk relasi lazy {@code project}) tetap terkelola.
 */
@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final UserRepository userRepository;
    private final com.example.app.modules.project.repository.ProjectCollaborationRepository projectCollaborationRepository;

    /** Pemanggil WAJIB member project ini (OWNER maupun COLLABORATOR). */
    public ProjectCollaboration requireMember(String userEmail, UUID projectId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan email " + userEmail + " tidak ditemukan (token valid tapi user sudah terhapus?)"
                ));

        return projectCollaborationRepository
                .findByProjectIdAndUserId(projectId, user.getId())
                .orElseThrow(ProjectNotFoundException::new);
    }

    /** Pemanggil WAJIB member DAN OWNER. 404 kalau bukan member, 403 kalau cuma COLLABORATOR. */
    public ProjectCollaboration requireOwner(String userEmail, UUID projectId, String forbiddenMessage) {
        ProjectCollaboration collaboration = requireMember(userEmail, projectId);

        if (!ProjectTeamCode.OWNER.equals(collaboration.getProjectTeam())) {
            throw new ProjectAccessForbiddenException(forbiddenMessage);
        }
        return collaboration;
    }
}
