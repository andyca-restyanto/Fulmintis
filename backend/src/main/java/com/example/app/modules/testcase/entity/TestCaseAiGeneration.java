// filepath: /backend/src/main/java/com/example/app/modules/testcase/entity/TestCaseAiGeneration.java
package com.example.app.modules.testcase.entity;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Satu job "generate test case dgn AI". Hasilnya adalah DRAFT (drafts_json) -- BUKAN test case: test case baru dibuat saat user
 * menyimpan hasil review (commit). Teks requirement SENGAJA TIDAK disimpan (bisa berisi dokumen internal): ia hanya diteruskan ke
 * thread pemroses lewat memori; yang tersimpan hanya panjangnya, versi prompt, token, dan model.
 * <p>
 * Baris ini juga menjadi dasar hitungan kuota harian, jadi TIDAK dihapus -- hanya drafts_json yang dikosongkan (setelah di-commit,
 * atau setelah lewat masa simpan).
 */
@Entity
@Table(name = "testcase_ai_generation", indexes = {
        @Index(name = "idx_testcase_ai_generation_user_created", columnList = "requested_by, created_at"),
        @Index(name = "idx_testcase_ai_generation_project_user", columnList = "project_id, requested_by")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseAiGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // Folder tujuan saat diminta (tanpa FK: folder boleh dihapus tanpa memutus riwayat kuota).
    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    // FREE / VIP saat diminta (audit biaya & batas draft saat commit).
    @Column(nullable = false, length = 10)
    private String tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCaseAiGenerationStatus status;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "include_negative", nullable = false)
    private boolean includeNegative;

    // Hanya panjang, bukan isinya.
    @Column(name = "requirement_length", nullable = false)
    private int requirementLength;

    @Column(name = "draft_count", nullable = false)
    private int draftCount;

    // JSON array draft; null setelah di-commit / kedaluwarsa / bila gagal.
    @Column(name = "drafts_json", columnDefinition = "TEXT")
    private String draftsJson;

    // true = keluaran AI terpotong; hanya draft yang lengkap yang diselamatkan.
    @Column(nullable = false)
    private boolean truncated;

    @Column(nullable = false)
    private boolean committed;

    @Column(name = "committed_count")
    private Integer committedCount;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

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
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
