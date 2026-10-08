// filepath: /backend/src/main/java/com/example/app/modules/automation/entity/AutomationGeneration.java
package com.example.app.modules.automation.entity;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import com.example.app.modules.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Satu job generate. Menyimpan SNAPSHOT setup (framework/bahasa/pola/struktur) saat
 * diminta, karena setup project bisa diubah sesudahnya dan hasil lama harus tetap
 * bisa ditelusuri. Hasil (daftar file) disimpan sebagai JSON teks.
 */
@Entity
@Table(name = "automation_generation", indexes = {
        @Index(name = "idx_automation_generation_project_created", columnList = "project_id, created_at"),
        @Index(name = "idx_automation_generation_user_created", columnList = "requested_by, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    // Tier saat diminta: FREE / VIP (untuk audit biaya).
    @Column(nullable = false, length = 10)
    private String tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationGenerationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationFramework framework;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutomationPattern pattern;

    @Column(name = "structure_notes", columnDefinition = "TEXT")
    private String structureNotes;

    // JSON array UUID test case, sesuai urutan pilihan user.
    @Column(name = "test_case_ids", nullable = false, columnDefinition = "TEXT")
    private String testCaseIds;

    @Column(name = "test_case_count", nullable = false)
    private int testCaseCount;

    // JSON array {path, content}; null selama belum SUCCEEDED.
    @Column(name = "result_files", columnDefinition = "TEXT")
    private String resultFiles;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    // Versi template prompt yang dipakai (penelusuran mutu hasil).
    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 100)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
