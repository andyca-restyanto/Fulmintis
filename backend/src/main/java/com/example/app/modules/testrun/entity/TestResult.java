// backend/src/main/java/com/example/app/modules/testrun/entity/TestResult.java
package com.example.app.modules.testrun.entity;

import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testrun.TestResultStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table "test_result" (requirement #5) -- baris MAPPING antara 1 test run
 * dgn 1 test case (requirement #2), SEKALIGUS nyimpen hasil eksekusinya
 * (status/evidence/comment). 1 baris = 1 test case yang di-run di dalam 1
 * test run tertentu.
 * <p>
 * Unique constraint (test_run_id, test_case_id) -- 1 test case cuma boleh
 * ke-mapping SEKALI ke test run yang sama (dicegah juga di level service
 * lewat existsByTestRunIdAndTestCaseId sebelum insert, unique constraint di
 * sini jaga-jaga race condition).
 */
@Entity
@Table(
        name = "test_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_test_result_run_case",
                columnNames = {"test_run_id", "test_case_id"}
        ),
        indexes = {
                @Index(name = "idx_test_result_test_run_id", columnList = "test_run_id"),
                @Index(name = "idx_test_result_test_case_id", columnList = "test_case_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    // Default NEW begitu test case baru di-mapping (requirement #2).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TestResultStatus status = TestResultStatus.NEW;

    // Nama FILE yang tersimpan di disk (bukan path absolut, bukan bytes
    // gambar/video-nya langsung) -- lihat shared.storage.FileStorageService
    // & TestRunController GET .../evidence. NULL selama belum ada evidence
    // yang di-upload.
    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(columnDefinition = "TEXT")
    private String comment;

    // Hasil per-step (centang/silang tiap step di halaman Execute), disimpan
    // sbg daftar nama enum TestStepResultStatus dipisah koma, urutan = urutan
    // step di test case, mis. "PASSED,FAILED,NEW". NULL/kosong = belum ada
    // step yang ditandai. Sengaja 1 kolom TEXT (bukan tabel terpisah) --
    // sama seperti test_step/expected_result di test_case.
    @Column(name = "step_results", columnDefinition = "TEXT")
    private String stepResults;

    // Snapshot test_step & expected_result test case PADA SAAT di-mapping ke
    // run ini. Hasil per-step (stepResults) berbasis URUTAN, jadi kalau test
    // case diedit setelah dieksekusi, tanda PASSED/FAILED akan bergeser ke
    // step yang salah -- dengan snapshot, run selalu menampilkan step yang
    // dulu benar-benar dieksekusi. NULL = baris lama (sebelum fitur ini) ->
    // service fallback ke test case live.
    @Column(name = "test_step_snapshot", columnDefinition = "TEXT")
    private String testStepSnapshot;

    @Column(name = "expected_result_snapshot", columnDefinition = "TEXT")
    private String expectedResultSnapshot;

    // Optimistic locking. DEFAULT 0 di columnDefinition WAJIB: ddl-auto=update
    // menambah kolom ini ke tabel yang sudah berisi -- tanpa default, baris
    // lama berversi NULL dan setiap UPDATE-nya gagal.
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Kapan hasil ini TERAKHIR dieksekusi: diisi saat status berubah ke selain
    // NEW, dikosongkan kalau kembali ke NEW. Sengaja BUKAN updated_at, karena
    // updated_at ikut berubah saat komentar/evidence/step diedit. Dipakai
    // dashboard project (Execution Timeline; Current Project Health tidak memakainya). Hanya menyimpan eksekusi
    // TERAKHIR per baris -- kalau hasil yang sama dieksekusi ulang, titik harinya
    // pindah dan riwayat lamanya tidak tersimpan (bukan tabel riwayat).
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

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
