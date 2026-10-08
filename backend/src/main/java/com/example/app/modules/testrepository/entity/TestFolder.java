// backend/src/main/java/com/example/app/modules/testrepository/entity/TestFolder.java
package com.example.app.modules.testrepository.entity;

import com.example.app.modules.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table "test_folder" -- test suite/folder di dalam 1 project (menu Test
 * Repository). Bisa nested (folder di dalam folder) lewat self-reference
 * `parent` -- NULL berarti folder ini ada di root project (requirement #2).
 * <p>
 * project & test_folder SAMA-SAMA ada di database "frontline", jadi FK asli
 * (@ManyToOne) valid dipakai & ditegakkan oleh database -- pola yang sama
 * dengan ProjectCollaboration -> Project.
 */
@Entity
@Table(name = "test_folder", indexes = {
        @Index(name = "idx_test_folder_project_id", columnList = "project_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "folder_name", nullable = false)
    private String folderName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // NULL = folder root (tidak punya parent folder) -- default sesuai
    // requirement #2. Kalau diisi, WAJIB folder lain yang project_id-nya
    // SAMA (divalidasi di TestFolderServiceImpl, bukan di level entity).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private TestFolder parent;

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
