// backend/src/main/java/com/example/app/modules/testrun/dto/TestResultResponseDTO.java
package com.example.app.modules.testrun.dto;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.TestStepResultStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response 1 baris test_result -- dikembalikan sbg bagian dari
 * TestRunDetailResponseDTO.testResults, dan sbg response langsung dari
 * endpoint update status/upload evidence.
 */
@Getter
@Setter
@Builder
public class TestResultResponseDTO {
    private UUID id;
    private UUID testRunId;

    // testCaseId + testCaseTitle SENGAJA di-denormalisasi dari TestCase
    // terkait di sini -- biar FE bisa render tabel "test case apa aja yang
    // di-run" tanpa perlu join manual/panggil API test case lagi.
    private UUID testCaseId;
    private String testCaseTitle;
    // Detail test case yang dibutuhkan halaman Execute (deskripsi, pre-condition,
    // step & expected result) -- SENGAJA di-denormalisasi di sini (sama seperti
    // testCaseTitle) supaya FE tidak perlu memanggil GET /test-cases/{id} per
    // baris. Itu juga menghindari 404 utk COLLABORATOR kalau test case-nya
    // belakangan di-archive (archived hanya boleh dilihat OWNER). Semuanya
    // nullable. testCaseTestStep & testCaseExpectedResult berformat sama
    // dgn TestCaseResponseDTO ("1. ...\n2. ...").
    private String testCaseDescription;
    private String testCasePrecondition;
    private String testCaseTestStep;
    private String testCaseExpectedResult;
    // Dibutuhkan tab Report > Run Details & Export Excel (kolom
    // Priority/Type/Scenario) -- sama alasannya dgn field testCaseDescription
    // dkk di atas: denormalisasi supaya FE/export tidak perlu panggil GET
    // test case terpisah. testCaseScenarioType nullable (field TestCase yang
    // sama juga nullable).
    private TestCasePriority testCasePriority;
    private TestCaseType testCaseType;
    private TestCaseScenarioType testCaseScenarioType;

    private TestResultStatus status;

    // NULL kalau belum ada evidence yang di-upload. Kalau ada, isinya path
    // relatif API utk DOWNLOAD file-nya (BUKAN nama file mentah di disk) --
    // lihat TestRunController GET .../evidence. FE tinggal pasang ini
    // langsung sbg href/src, tidak perlu tahu detail storage.
    private String evidenceUrl;

    private String comment;
    // Hasil per-step, urutan = urutan step di test case. TIDAK PERNAH null
    // (list kosong kalau belum ada step yang ditandai); panjangnya bisa
    // beda dgn jumlah step saat ini kalau test case diedit setelah run dibuat.
    private List<TestStepResultStatus> stepResults;
    // Versi optimistic lock -- kirim balik di UpdateTestResultRequestDTO.version
    // supaya perubahan yang menimpa data terbaru orang lain ditolak (409).
    private Long version;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
