// filepath: /backend/src/main/java/com/example/app/modules/automation/entity/AutomationSetup.java
package com.example.app.modules.automation.entity;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import com.example.app.modules.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Struktur/arsitektur automation sebuah project. MAKSIMAL SATU per project --
 * ditegakkan unique constraint di DATABASE (bukan hanya pengecekan di kode, yang
 * bisa kebobolan oleh dua request bersamaan).
 */
@Entity
@Table(name = "automation_setup", uniqueConstraints = {
        @UniqueConstraint(name = "uq_automation_setup_project", columnNames = "project_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationSetup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationFramework framework;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutomationPattern pattern;

    // Struktur folder & aturan penamaan (teks bebas, opsional).
    @Column(name = "structure_notes", columnDefinition = "TEXT")
    private String structureNotes;

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
