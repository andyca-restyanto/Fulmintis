// backend/src/main/java/com/example/app/modules/project/entity/ProjectCollaboration.java
package com.example.app.modules.project.entity;

import com.example.app.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tabel junction Project <-> User. BEDA dengan User.userType (yang cuma
 * string code karena master_data ada di database TERPISAH), di sini
 * Project dan User SAMA-SAMA ada di database "frontline" -- jadi FK relasi
 * JPA (@ManyToOne) valid dipakai dan referential integrity-nya beneran
 * ditegakkan oleh database.
 *
 * Kolom "project_team" tetap string code (bukan relasi), karena tabel
 * project_team ada di database "master_data" yang terpisah.
 */
@Entity
@Table(name = "project_collaboration", indexes = {
        @Index(name = "idx_project_collab_user_id", columnList = "user_id"),
        @Index(name = "idx_project_collab_project_id", columnList = "project_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCollaboration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Kode dari project_team.description (database master_data), contoh: "OWNER"
    @Column(name = "project_team", nullable = false)
    private String projectTeam;

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
