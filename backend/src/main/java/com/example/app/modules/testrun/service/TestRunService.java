// backend/src/main/java/com/example/app/modules/testrun/service/TestRunService.java
package com.example.app.modules.testrun.service;

import com.example.app.modules.testrun.dto.AddTestCasesToTestRunRequestDTO;
import com.example.app.modules.testrun.dto.CreateTestRunRequestDTO;
import com.example.app.modules.testrun.dto.SyncTestRunCasesRequestDTO;
import com.example.app.modules.testrun.dto.TestResultResponseDTO;
import com.example.app.modules.testrun.dto.TestRunDetailResponseDTO;
import com.example.app.modules.testrun.dto.TestRunResponseDTO;
import com.example.app.modules.testrun.dto.UpdateTestResultRequestDTO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface TestRunService {

    /**
     * Buat test run baru (requirement #1), OPSIONAL langsung mapping test
     * case ke dalamnya kalau {@code request.testCaseIds} diisi (requirement
     * #2) -- sesuai flow yang diminta (isi title/description -> pilih test
     * case -> 1x klik "Create Test Run").
     * <p>
     * HANYA boleh OWNER project (requirement #6).
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.project.exception.ProjectAccessForbiddenException
     *         kalau user yang minta BUKAN OWNER (403, requirement #6).
     * @throws com.example.app.modules.testcase.exception.TestCaseNotFoundException
     *         kalau ada testCaseIds yang tidak valid utk project ini /
     *         berstatus archived (404).
     */
    TestRunDetailResponseDTO createTestRun(String userEmail, UUID projectId, CreateTestRunRequestDTO request);

    /**
     * Daftar semua test run di 1 project (menu Test Run), ringkas (tanpa
     * detail per-test-case). Boleh OWNER maupun COLLABORATOR.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     */
    List<TestRunResponseDTO> listTestRuns(String userEmail, UUID projectId);

    /**
     * Detail PENUH (termasuk testResults per test run) dari SEMUA test run
     * di project ini, sekali fetch -- dipakai modul report (tab Run Details
     * & Export Excel, lihat ReportServiceImpl/ReportExportServiceImpl) supaya
     * tidak perlu duplikasi logic denormalisasi TestResultResponseDTO
     * (evidence URL, parse stepResults, dst) yang sudah ada di sini. Boleh
     * OWNER maupun COLLABORATOR, sama seperti {@link #listTestRuns}.
     */
    List<TestRunDetailResponseDTO> listTestRunDetails(String userEmail, UUID projectId);

    /**
     * Detail 1 test run, TERMASUK daftar test case yang sudah di-mapping +
     * hasil eksekusinya masing-masing (requirement #2). Boleh OWNER maupun
     * COLLABORATOR.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testrun.exception.TestRunNotFoundException
     *         kalau testRunId tidak valid utk project ini (404).
     */
    TestRunDetailResponseDTO getTestRunDetail(String userEmail, UUID projectId, UUID testRunId);

    /**
     * Tambah test case ke test run yang SUDAH ADA (requirement #2). Boleh
     * OWNER MAUPUN COLLABORATOR (requirement #7 -- beda dgn createTestRun
     * yang OWNER-only). testCaseId yang SUDAH ke-mapping sebelumnya di-skip
     * diam-diam (idempotent, bukan error) -- supaya user bisa multi-select
     * bebas tanpa perlu tahu mana yang sudah/belum ke-mapping.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testrun.exception.TestRunNotFoundException
     *         kalau testRunId tidak valid utk project ini (404).
     * @throws com.example.app.modules.testcase.exception.TestCaseNotFoundException
     *         kalau ada testCaseIds yang tidak valid utk project ini /
     *         berstatus archived (404).
     */
    TestRunDetailResponseDTO addTestCasesToRun(
            String userEmail, UUID projectId, UUID testRunId, AddTestCasesToTestRunRequestDTO request
    );

    /**
     * Sinkronkan daftar test case di run ini dgn daftar AKHIR yang dikirim FE
     * (checklist Manage Cases): id yang belum ke-mapping DITAMBAH, id yang
     * sudah ke-mapping tapi TIDAK ada di daftar DILEPAS (uncheck = delete
     * mapping; hasil eksekusi & file evidence-nya ikut terhapus permanen).
     * Semuanya dalam 1 transaksi -- kalau salah satu id tidak valid, TIDAK
     * ADA perubahan sama sekali (tidak setengah tertambah/setengah terhapus).
     * File evidence baru dihapus dari disk SETELAH transaksi berhasil commit.
     * <p>
     * Boleh OWNER MAUPUN COLLABORATOR (sama dgn add/remove). List kosong =
     * kosongkan run (status balik PENDING). Validasi "ada, satu project,
     * ACTIVE" hanya berlaku utk id yang BARU ditambahkan -- test case yang
     * sudah ke-mapping lalu belakangan di-archive tetap dipertahankan kalau
     * id-nya masih dikirim.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testrun.exception.TestRunNotFoundException
     *         kalau testRunId tidak valid utk project ini (404).
     * @throws com.example.app.modules.testcase.exception.TestCaseNotFoundException
     *         kalau ada id BARU yang tidak valid utk project ini / archived (404).
     */
    TestRunDetailResponseDTO syncTestCasesInRun(
            String userEmail, UUID projectId, UUID testRunId, SyncTestRunCasesRequestDTO request
    );

    /**
     * Requirement tambahan #1 & #3: lepas 1 test case dari test run
     * (kebalikan dari {@link #addTestCasesToRun}) -- baris test_result
     * (mapping + hasil eksekusi + evidence file-nya kalau ada) dihapus
     * PERMANEN. Boleh OWNER MAUPUN COLLABORATOR, DISAMAKAN dgn aturan
     * addTestCasesToRun (requirement tambahan #3: "Manage test case dapat
     * dilakukan oleh owner / collaborator") -- BUKAN owner-only seperti
     * createTestRun/deleteTestRun (requirement tambahan #2 cuma berlaku
     * utk hapus TEST RUN-nya, bukan utk lepas 1 test case dari dalamnya).
     * <p>
     * Efek samping: status TestRun induk ikut di-recompute (mis. run yang
     * tadinya RUNNING krn ada test case FAILED, begitu test case FAILED
     * itu di-remove & sisanya semua PASSED, otomatis naik jadi FINISHED;
     * kalau SEMUA test case di-remove sampai run kosong, otomatis balik ke
     * PENDING).
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testrun.exception.TestRunNotFoundException
     *         kalau testRunId tidak valid utk project ini (404).
     * @throws com.example.app.modules.testrun.exception.TestResultNotFoundException
     *         kalau testCaseId ini TIDAK sedang ke-mapping ke test run ini
     *         (404) -- termasuk kalau testCaseId-nya sendiri tidak
     *         valid/tidak pernah ada.
     */
    TestRunDetailResponseDTO removeTestCaseFromRun(
            String userEmail, UUID projectId, UUID testRunId, UUID testCaseId
    );

    /**
     * Catat hasil eksekusi 1 test case di dalam test run (status + comment,
     * requirement #5). Boleh OWNER maupun COLLABORATOR. Efek samping: status
     * TestRun induk ikut di-otomasi (PENDING -> RUNNING begitu ada 1 hasil
     * yang bukan NEW; -> FINISHED HANYA kalau SEMUA hasil sudah PASSED --
     * kalau ada hasil selain PASSED, tetap RUNNING) -- lihat
     * TestRunServiceImpl.recomputeRunStatus().
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.testrun.exception.TestRunNotFoundException
     *         kalau testRunId tidak valid utk project ini (404).
     * @throws com.example.app.modules.testrun.exception.TestResultNotFoundException
     *         kalau testResultId tidak valid utk test run ini (404).
     */
    TestResultResponseDTO updateTestResult(
            String userEmail, UUID projectId, UUID testRunId, UUID testResultId, UpdateTestResultRequestDTO request
    );

    /**
     * Upload/replace evidence (image/video) utk 1 test result (requirement
     * #5). Evidence lama (kalau ada) DIHAPUS dari disk & digantikan yang
     * baru. Boleh OWNER maupun COLLABORATOR.
     *
     * @throws com.example.app.modules.testrun.exception.EvidenceUploadException
     *         kalau file kosong, bukan image/video, atau melebihi ukuran
     *         maksimum (400).
     */
    TestResultResponseDTO uploadEvidence(
            String userEmail, UUID projectId, UUID testRunId, UUID testResultId, MultipartFile file
    );

    /**
     * Ambil isi file evidence 1 test result utk di-stream balik ke client.
     *
     * @throws com.example.app.modules.testrun.exception.TestResultNotFoundException
     *         kalau testResultId tidak valid, ATAU belum ada evidence yang
     *         di-upload sama sekali (404).
     */
    LoadedEvidence loadEvidence(String userEmail, UUID projectId, UUID testRunId, UUID testResultId);

    /**
     * Hapus test run beserta SEMUA test result (mapping test case) di
     * dalamnya, TERMASUK file evidence yang sudah ter-upload -- HARD DELETE
     * permanen (beda dgn test case yang soft-delete/archive), karena tidak
     * ada requirement yang minta test run bisa "dipulihkan" lagi. HANYA
     * boleh OWNER project (disamakan dgn aturan createTestRun, requirement
     * #6).
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     * @throws com.example.app.modules.project.exception.ProjectAccessForbiddenException
     *         kalau user yang minta BUKAN OWNER (403).
     * @throws com.example.app.modules.testrun.exception.TestRunNotFoundException
     *         kalau testRunId tidak valid utk project ini (404).
     */
    void deleteTestRun(String userEmail, UUID projectId, UUID testRunId);

    /**
     * Hasil {@link #loadEvidence} -- bungkus Resource + nama file asli +
     * content-type, supaya controller tinggal set header response tanpa
     * perlu tahu detail storage.
     */
    record LoadedEvidence(Resource resource, String storedFileName, String contentType, boolean inlineSafe) {
    }
}
