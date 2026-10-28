// backend/src/main/java/com/example/app/modules/testrun/service/impl/TestRunServiceImpl.java
package com.example.app.modules.testrun.service.impl;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.exception.TestCaseNotFoundException;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.TestRunStatus;
import com.example.app.modules.testrun.TestStepResultStatus;
import com.example.app.modules.testrun.dto.AddTestCasesToTestRunRequestDTO;
import com.example.app.modules.testrun.dto.CreateTestRunRequestDTO;
import com.example.app.modules.testrun.dto.SyncTestRunCasesRequestDTO;
import com.example.app.modules.testrun.dto.TestResultResponseDTO;
import com.example.app.modules.testrun.dto.TestRunDetailResponseDTO;
import com.example.app.modules.testrun.dto.TestRunResponseDTO;
import com.example.app.modules.testrun.dto.UpdateTestResultRequestDTO;
import com.example.app.modules.testrun.entity.TestResult;
import com.example.app.modules.testrun.entity.TestRun;
import com.example.app.modules.testrun.exception.EvidenceUploadException;
import com.example.app.modules.testrun.exception.TestResultConflictException;
import com.example.app.modules.testrun.exception.TestResultNotFoundException;
import com.example.app.modules.testrun.exception.TestRunNotFoundException;
import com.example.app.modules.testrun.repository.TestResultRepository;
import com.example.app.modules.testrun.repository.TestRunRepository;
import com.example.app.modules.testrun.service.TestRunService;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.storage.EvidenceFileType;
import com.example.app.shared.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestRunServiceImpl implements TestRunService {

    // Subfolder di dalam root storage (app.storage.base-dir) -- lihat
    // LocalFileStorageService. Dipisah dari root supaya nanti gampang kalau
    // ada jenis upload lain (bukan evidence test run) yg butuh subfolder
    // sendiri juga.
    private static final String EVIDENCE_SUBDIRECTORY = "test-evidence";
    private static final long MAX_EVIDENCE_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB

    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectAccessService projectAccessService;
    private final FileStorageService fileStorageService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public TestRunDetailResponseDTO createTestRun(String userEmail, UUID projectId, CreateTestRunRequestDTO request) {
        // Requirement #6: HANYA OWNER yang boleh create test run -- 403
        // (bukan 404) krn user di titik ini sudah terbukti member yang sah.
        ProjectCollaboration myCollaboration = projectAccessService.requireOwner(
                userEmail, projectId, "Hanya OWNER project yang bisa membuat test run.");

        Project project = myCollaboration.getProject();

        TestRun testRun = TestRun.builder()
                .project(project)
                .title(request.getTitle().trim())
                .description(blankToNull(request.getDescription()))
                // status default PENDING via @Builder.Default di entity.
                .createdBy(userEmail)
                .updatedBy(userEmail)
                .build();

        TestRun savedTestRun = testRunRepository.save(testRun);

        // Requirement #1 & #2 digabung dalam 1x create sesuai flow yang
        // diminta -- kalau user sudah pilih test case sebelum klik "Create
        // Test Run", langsung di-mapping di sini juga (dalam transaksi yang
        // sama, jadi test run TIDAK PERNAH ada tanpa test case-nya kalau
        // memang begitu yang di-request).
        List<UUID> testCaseIds = distinctNonNull(request.getTestCaseIds());
        mapTestCasesToRun(savedTestRun, testCaseIds, userEmail);
        // Test case yang baru di-mapping SELALU berstatus NEW -- recompute
        // di sini supaya konsisten (harusnya tetap PENDING di titik ini,
        // TIDAK PERNAH otomatis FINISHED cuma krn baru dibuat).
        recomputeRunStatus(savedTestRun, userEmail);

        activityLogService.log(userEmail, "CREATE_TEST_RUN");

        return toDetailResponseDTO(savedTestRun);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestRunResponseDTO> listTestRuns(String userEmail, UUID projectId) {
        // List boleh OWNER maupun COLLABORATOR -- cukup validasi membership.
        projectAccessService.requireMember(userEmail, projectId);

        List<TestRun> testRuns = testRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        // 1 query GROUP BY utk seluruh run (bukan 1 query per run).
        Map<UUID, Map<TestResultStatus, Long>> countsByRun = new HashMap<>();
        for (TestResultRepository.RunStatusCount row : testResultRepository.countGroupedByRunAndStatus(projectId)) {
            countsByRun.computeIfAbsent(row.getRunId(), k -> new EnumMap<>(TestResultStatus.class))
                    .put(row.getStatus(), row.getTotal());
        }

        return testRuns.stream()
                .map(run -> toResponseDTO(run, countsByRun.getOrDefault(run.getId(), Map.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestRunDetailResponseDTO> listTestRunDetails(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);

        List<TestRun> testRuns = testRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        if (testRuns.isEmpty()) {
            return List.of();
        }

        // 1 query utk SEMUA test result semua run (+ test case-nya via
        // EntityGraph), lalu dikelompokkan per run di memori.
        List<UUID> runIds = testRuns.stream().map(TestRun::getId).toList();
        Map<UUID, List<TestResult>> resultsByRun = testResultRepository
                .findByTestRunIdInOrderByCreatedAtAsc(runIds).stream()
                .collect(Collectors.groupingBy(r -> r.getTestRun().getId()));

        return testRuns.stream()
                .map(run -> toDetailResponseDTO(run, resultsByRun.getOrDefault(run.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TestRunDetailResponseDTO getTestRunDetail(String userEmail, UUID projectId, UUID testRunId) {
        projectAccessService.requireMember(userEmail, projectId);

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);

        return toDetailResponseDTO(testRun);
    }

    @Override
    @Transactional
    public TestRunDetailResponseDTO addTestCasesToRun(
            String userEmail, UUID projectId, UUID testRunId, AddTestCasesToTestRunRequestDTO request
    ) {
        // Requirement #7: OWNER MAUPUN COLLABORATOR boleh nambah test case
        // ke test run yang sudah ada -- cukup validasi membership, TIDAK
        // ada cek role tambahan (beda dgn createTestRun yang OWNER-only).
        projectAccessService.requireMember(userEmail, projectId);

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);

        List<UUID> testCaseIds = distinctNonNull(request.getTestCaseIds());
        mapTestCasesToRun(testRun, testCaseIds, userEmail);
        // Kalau run ini sebelumnya sudah FINISHED (semua PASSED) lalu
        // ditambah test case baru (otomatis berstatus NEW), status HARUS
        // turun lagi jadi RUNNING -- "semua PASSED" jadi tidak berlaku lagi
        // begitu ada tambahan test case yang belum dieksekusi.
        recomputeRunStatus(testRun, userEmail);

        activityLogService.log(userEmail, "ADD_TEST_CASES_TO_TEST_RUN");

        return toDetailResponseDTO(testRun);
    }

    @Override
    @Transactional
    public TestRunDetailResponseDTO syncTestCasesInRun(
            String userEmail, UUID projectId, UUID testRunId, SyncTestRunCasesRequestDTO request
    ) {
        // OWNER maupun COLLABORATOR -- cukup validasi membership, sama
        // seperti addTestCasesToRun / removeTestCaseFromRun.
        projectAccessService.requireMember(userEmail, projectId);

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);

        Set<UUID> desiredIds = new java.util.LinkedHashSet<>(distinctNonNull(request.getTestCaseIds()));

        List<TestResult> existingResults = testResultRepository.findByTestRunIdOrderByCreatedAtAsc(testRunId);
        Set<UUID> existingIds = existingResults.stream()
                .map(r -> r.getTestCase().getId())
                .collect(Collectors.toSet());

        // 1) TAMBAH dulu yang baru tercentang. Validasi (ada, satu project,
        // ACTIVE) hanya utk id BARU -- test case yang sudah ke-mapping lalu
        // di-archive belakangan tetap boleh dipertahankan. Kalau ada id baru
        // tidak valid, exception dilempar SEBELUM ada yang dihapus &
        // transaksi rollback penuh.
        List<UUID> idsToAdd = desiredIds.stream().filter(id -> !existingIds.contains(id)).toList();
        mapTestCasesToRun(testRun, idsToAdd, userEmail);

        // 2) LEPAS yang di-uncheck (ada di run, tidak ada di daftar akhir).
        List<TestResult> resultsToRemove = existingResults.stream()
                .filter(r -> !desiredIds.contains(r.getTestCase().getId()))
                .toList();
        List<String> evidenceFilesToDelete = resultsToRemove.stream()
                .map(TestResult::getEvidence)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        testResultRepository.deleteAll(resultsToRemove);

        // File evidence dihapus dari disk HANYA setelah transaksi commit
        // (lihat FileStorageService.deleteAfterCommit).
        evidenceFilesToDelete.forEach(name -> fileStorageService.deleteAfterCommit(EVIDENCE_SUBDIRECTORY, name));

        // Status run dihitung ulang (mis. FINISHED -> RUNNING kalau ada case
        // baru berstatus NEW; RUNNING -> FINISHED kalau sisa yang FAILED
        // dilepas; kosong -> PENDING).
        recomputeRunStatus(testRun, userEmail);

        if (!idsToAdd.isEmpty() || !resultsToRemove.isEmpty()) {
            activityLogService.log(userEmail, "SYNC_TEST_CASES_IN_TEST_RUN");
        }

        return toDetailResponseDTO(testRun);
    }

    @Override
    @Transactional
    public TestRunDetailResponseDTO removeTestCaseFromRun(
            String userEmail, UUID projectId, UUID testRunId, UUID testCaseId
    ) {
        // Requirement tambahan #3: OWNER MAUPUN COLLABORATOR boleh lepas
        // test case dari test run -- permission level DISAMAKAN dgn
        // addTestCasesToRun (cukup validasi membership, TIDAK ada cek role
        // tambahan seperti di createTestRun/deleteTestRun).
        projectAccessService.requireMember(userEmail, projectId);

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);

        // Requirement tambahan #1: lepas mapping-nya. Kalau testCaseId ini
        // memang TIDAK sedang ke-mapping ke run ini (baik krn sudah pernah
        // dilepas sebelumnya, ATAU testCaseId-nya sendiri tidak valid) --
        // balas 404, BUKAN di-skip diam-diam (beda dgn addTestCasesToRun
        // yang idempotent) supaya FE tahu pasti aksi remove-nya gagal.
        TestResult testResult = testResultRepository.findByTestRunIdAndTestCaseId(testRunId, testCaseId)
                .orElseThrow(() -> new TestResultNotFoundException(
                        "Test case ini tidak ada di test run ini."
                ));

        // Sama seperti deleteTestRun: file dihapus SETELAH commit.
        fileStorageService.deleteAfterCommit(EVIDENCE_SUBDIRECTORY, testResult.getEvidence());
        testResultRepository.delete(testResult);

        // Requirement tambahan #4 TETAP terjaga setelah remove ini: test
        // case yang baru dilepas dari run ini BOLEH langsung di-map lagi ke
        // run LAIN kapan saja (tidak ada larangan lintas run), atau
        // di-map ULANG ke run ini sendiri via addTestCasesToRun (bukan
        // dianggap "dobel" krn baris lama sudah benar-benar terhapus).
        recomputeRunStatus(testRun, userEmail);

        activityLogService.log(userEmail, "REMOVE_TEST_CASE_FROM_TEST_RUN");

        return toDetailResponseDTO(testRun);
    }

    @Override
    @Transactional
    public TestResultResponseDTO updateTestResult(
            String userEmail, UUID projectId, UUID testRunId, UUID testResultId, UpdateTestResultRequestDTO request
    ) {
        // Update hasil eksekusi boleh OWNER maupun COLLABORATOR -- tidak
        // ada requirement eksplisit yang membatasi ini ke OWNER saja (beda
        // dgn createTestRun), jadi disamakan dgn aturan addTestCasesToRun.
        projectAccessService.requireMember(userEmail, projectId);

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);
        TestResult testResult = findTestResultOrThrow(testResultId, testRun.getId());

        // Cek konflik (opsional): kalau klien mengirim versi yang dia lihat dan
        // ternyata sudah berubah (tester lain menyimpan lebih dulu) -> 409,
        // bukan menimpa diam-diam. Klien lama tanpa field version tetap jalan
        // (hanya terlindungi optimistic lock @Version untuk request bersamaan).
        if (request.getVersion() != null && !request.getVersion().equals(testResult.getVersion())) {
            throw new TestResultConflictException();
        }

        // executedAt hanya berubah kalau STATUS berubah (atau baris lama non-NEW
        // yang belum punya executedAt) -- bukan saat komentar/step saja yang
        // diedit. Kembali ke NEW = dianggap belum dieksekusi.
        TestResultStatus previousStatus = testResult.getStatus();
        TestResultStatus newStatus = request.getStatus();
        if (newStatus == TestResultStatus.NEW) {
            testResult.setExecutedAt(null);
        } else if (newStatus != previousStatus || testResult.getExecutedAt() == null) {
            testResult.setExecutedAt(LocalDateTime.now());
        }

        testResult.setStatus(newStatus);
        testResult.setComment(blankToNull(request.getComment()));
        // null = tidak diubah (request lama yang cuma status/comment tidak
        // boleh menghapus tanda step yang sudah tersimpan).
        if (request.getStepResults() != null) {
            testResult.setStepResults(serializeStepResults(request.getStepResults()));
        }
        testResult.setUpdatedBy(userEmail);
        // saveAndFlush: version naik SEKARANG, supaya DTO balasan membawa
        // versi terbaru (klien memakainya utk request berikutnya).
        TestResult savedResult = testResultRepository.saveAndFlush(testResult);

        recomputeRunStatus(testRun, userEmail);

        activityLogService.log(userEmail, "UPDATE_TEST_RESULT");

        return toResultResponseDTO(savedResult, projectId);
    }

    @Override
    @Transactional
    public TestResultResponseDTO uploadEvidence(
            String userEmail, UUID projectId, UUID testRunId, UUID testResultId, MultipartFile file
    ) {
        projectAccessService.requireMember(userEmail, projectId);

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);
        TestResult testResult = findTestResultOrThrow(testResultId, testRun.getId());

        // Tipe & ekstensi ditentukan dari ISI file (magic bytes), BUKAN dari
        // Content-Type/nama file kiriman klien -- lihat EvidenceFileType.
        EvidenceFileType detectedType = validateEvidenceFile(file);

        // Replace: file baru ditulis dulu; file LAMA baru dihapus SETELAH
        // transaksi commit (deleteAfterCommit), dan file BARU dihapus kalau
        // transaksi rollback (deleteIfRolledBack) -- tidak ada evidence yang
        // hilang / yatim walau proses gagal di tengah.
        String oldStoredFileName = testResult.getEvidence();
        String newStoredFileName = fileStorageService.store(file, EVIDENCE_SUBDIRECTORY, detectedType.extension());
        fileStorageService.deleteIfRolledBack(EVIDENCE_SUBDIRECTORY, newStoredFileName);
        fileStorageService.deleteAfterCommit(EVIDENCE_SUBDIRECTORY, oldStoredFileName);

        testResult.setEvidence(newStoredFileName);
        testResult.setUpdatedBy(userEmail);
        TestResult savedResult = testResultRepository.saveAndFlush(testResult);

        activityLogService.log(userEmail, "UPLOAD_TEST_RESULT_EVIDENCE");

        return toResultResponseDTO(savedResult, projectId);
    }

    @Override
    @Transactional
    public void deleteTestRun(String userEmail, UUID projectId, UUID testRunId) {
        // Disamakan dgn aturan createTestRun (requirement #6) -- HANYA
        // OWNER yang boleh delete test run.
        projectAccessService.requireOwner(
                userEmail, projectId, "Hanya OWNER project yang bisa menghapus test run.");

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);

        // File evidence dihapus SETELAH commit (bukan sebelum): kalau
        // transaksi rollback, baris DB kembali dan file-nya harus tetap ada.
        List<TestResult> results = testResultRepository.findByTestRunIdOrderByCreatedAtAsc(testRunId);
        for (TestResult result : results) {
            fileStorageService.deleteAfterCommit(EVIDENCE_SUBDIRECTORY, result.getEvidence());
        }

        // test_result WAJIB dihapus duluan (FK test_run_id NOT NULL
        // REFERENCES test_run) sebelum baris test_run-nya sendiri dihapus.
        testResultRepository.deleteByTestRunId(testRunId);
        testRunRepository.delete(testRun);

        activityLogService.log(userEmail, "DELETE_TEST_RUN");
    }

    @Override
    @Transactional(readOnly = true)
    public LoadedEvidence loadEvidence(String userEmail, UUID projectId, UUID testRunId, UUID testResultId) {
        projectAccessService.requireMember(userEmail, projectId);

        TestRun testRun = findTestRunOrThrow(testRunId, projectId);
        TestResult testResult = findTestResultOrThrow(testResultId, testRun.getId());

        String storedFileName = testResult.getEvidence();
        if (storedFileName == null) {
            throw new TestResultNotFoundException("Test result ini belum punya evidence yang di-upload.");
        }

        org.springframework.core.io.Resource resource;
        try {
            resource = fileStorageService.loadAsResource(EVIDENCE_SUBDIRECTORY, storedFileName);
        } catch (IllegalStateException fileMissingOnDisk) {
            // Baris DB masih nunjuk ke file yang sudah tidak ada di disk
            // (mis. folder uploads/ terhapus). Balas 404, bukan 500.
            throw new TestResultNotFoundException("File evidence tidak ditemukan di storage.");
        }
        // Content-Type ditentukan dari whitelist (bukan tebakan nama file).
        // File lama yang ekstensinya di luar whitelist (mis. .html/.svg dari
        // sebelum validasi isi file ada) disajikan sebagai biner + attachment
        // -- TIDAK PERNAH inline, supaya tidak bisa dieksekusi browser.
        var knownType = EvidenceFileType.fromStoredFileName(storedFileName);
        String contentType = knownType.map(EvidenceFileType::contentType).orElse("application/octet-stream");

        return new LoadedEvidence(resource, storedFileName, contentType, knownType.isPresent());
    }

    // ---- helper privat ----

    private TestRun findTestRunOrThrow(UUID testRunId, UUID projectId) {
        return testRunRepository.findById(testRunId)
                .filter(tr -> tr.getProject().getId().equals(projectId))
                .orElseThrow(TestRunNotFoundException::new);
    }

    private TestResult findTestResultOrThrow(UUID testResultId, UUID testRunId) {
        return testResultRepository.findById(testResultId)
                .filter(tr -> tr.getTestRun().getId().equals(testRunId))
                .orElseThrow(TestResultNotFoundException::new);
    }

    private List<UUID> distinctNonNull(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        return List.copyOf(distinct);
    }

    // Requirement #2: mapping test case ke test run. Dipakai createTestRun()
    // (mapping awal) MAUPUN addTestCasesToRun() (nambah belakangan) -- logic
    // validasi & idempotency-nya SAMA persis di kedua kasus itu.
    private void mapTestCasesToRun(TestRun testRun, List<UUID> testCaseIds, String userEmail) {
        UUID projectId = testRun.getProject().getId();

        for (UUID testCaseId : testCaseIds) {
            // Requirement #2 (konsisten dgn requirement soft-delete test
            // case): test case WAJIB ada, di project yang sama, DAN masih
            // ACTIVE (test case yang sudah di-archive tidak boleh ikut
            // di-run) -- pakai exception yang sama dgn modul testcase.
            TestCase testCase = testCaseRepository.findById(testCaseId)
                    .filter(tc -> tc.getProject().getId().equals(projectId))
                    .filter(tc -> tc.getStatus() == TestCaseStatus.ACTIVE)
                    .orElseThrow(TestCaseNotFoundException::new);

            // Idempotent -- test case yang SUDAH ke-mapping sebelumnya
            // di-skip diam-diam (bukan error), lihat javadoc
            // TestRunService.addTestCasesToRun().
            if (testResultRepository.existsByTestRunIdAndTestCaseId(testRun.getId(), testCaseId)) {
                continue;
            }

            TestResult testResult = TestResult.builder()
                    .testRun(testRun)
                    .testCase(testCase)
                    // Snapshot step supaya hasil per-step tidak bergeser kalau
                    // test case diedit setelah dieksekusi (lihat TestResult).
                    .testStepSnapshot(testCase.getTestStep())
                    .expectedResultSnapshot(testCase.getExpectedResult())
                    // status default NEW via @Builder.Default di entity.
                    .createdBy(userEmail)
                    .updatedBy(userEmail)
                    .build();

            testResultRepository.save(testResult);
        }
    }

    // Otomasi status TestRun (lihat javadoc TestRunStatus): dipanggil
    // SETIAP KALI 1 test result di-update hasilnya, ATAUPUN setiap kali
    // daftar test case-nya berubah (nambah via addTestCasesToRun, atau
    // lepas via removeTestCaseFromRun -- requirement tambahan #1). PENDING
    // -> RUNNING begitu ada minimal 1 hasil yang bukan NEW lagi; ->
    // FINISHED HANYA kalau SEMUA hasil sudah PASSED (bukan sekadar "bukan
    // NEW" lagi) -- kalau ada 1 saja hasil yang FAILED/BLOCKED/PENDING
    // (status), run TETAP RUNNING, tidak dianggap selesai. Test run TANPA
    // test case sama sekali (baik krn belum pernah ditambahkan APAPUN,
    // ATAUPUN krn SEMUA test case-nya baru saja dilepas lewat
    // removeTestCaseFromRun) SELALU balik/tetap PENDING -- tidak ada apa
    // pun utk "dieksekusi", jadi tidak mungkin RUNNING/FINISHED.
    private void recomputeRunStatus(TestRun testRun, String userEmail) {
        List<TestResult> allResults = testResultRepository.findByTestRunIdOrderByCreatedAtAsc(testRun.getId());

        TestRunStatus newStatus;
        if (allResults.isEmpty()) {
            newStatus = TestRunStatus.PENDING;
        } else {
            boolean anyExecuted = allResults.stream().anyMatch(r -> r.getStatus() != TestResultStatus.NEW);
            boolean allPassed = allResults.stream().allMatch(r -> r.getStatus() == TestResultStatus.PASSED);

            newStatus = allPassed ? TestRunStatus.FINISHED
                    : anyExecuted ? TestRunStatus.RUNNING
                    : TestRunStatus.PENDING;
        }

        if (newStatus != testRun.getStatus()) {
            testRun.setStatus(newStatus);
            testRun.setUpdatedBy(userEmail);
            testRunRepository.save(testRun);
        }
    }

    private EvidenceFileType validateEvidenceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EvidenceUploadException("File evidence tidak boleh kosong.");
        }

        if (file.getSize() > MAX_EVIDENCE_SIZE_BYTES) {
            throw new EvidenceUploadException("Ukuran file evidence maksimal 50MB.");
        }

        byte[] header;
        try (java.io.InputStream in = file.getInputStream()) {
            header = in.readNBytes(EvidenceFileType.HEADER_BYTES);
        } catch (java.io.IOException e) {
            throw new EvidenceUploadException("File evidence tidak bisa dibaca.");
        }

        return EvidenceFileType.detect(header).orElseThrow(() -> new EvidenceUploadException(
                "Evidence hanya boleh berupa png, jpg, gif, webp, mp4, mov, atau webm."));
    }

    private String serializeStepResults(List<TestStepResultStatus> stepResults) {
        return stepResults.stream()
                .map(status -> status == null ? TestStepResultStatus.NEW : status)
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private List<TestStepResultStatus> parseStepResults(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(value -> {
                    try {
                        return TestStepResultStatus.valueOf(value);
                    } catch (IllegalArgumentException unknownValue) {
                        return TestStepResultStatus.NEW;
                    }
                })
                .toList();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TestRunResponseDTO toResponseDTO(TestRun testRun, Map<TestResultStatus, Long> countByStatus) {
        long total = countByStatus.values().stream().mapToLong(Long::longValue).sum();

        return TestRunResponseDTO.builder()
                .id(testRun.getId())
                .projectId(testRun.getProject().getId())
                .title(testRun.getTitle())
                .description(testRun.getDescription())
                .status(testRun.getStatus())
                .testCaseCount(total)
                .passedCount(countByStatus.getOrDefault(TestResultStatus.PASSED, 0L))
                .failedCount(countByStatus.getOrDefault(TestResultStatus.FAILED, 0L))
                .blockedCount(countByStatus.getOrDefault(TestResultStatus.BLOCKED, 0L))
                .createdAt(testRun.getCreatedAt())
                .createdBy(testRun.getCreatedBy())
                .updatedAt(testRun.getUpdatedAt())
                .updatedBy(testRun.getUpdatedBy())
                .build();
    }

    private TestRunDetailResponseDTO toDetailResponseDTO(TestRun testRun) {
        return toDetailResponseDTO(testRun, testResultRepository.findByTestRunIdOrderByCreatedAtAsc(testRun.getId()));
    }

    private TestRunDetailResponseDTO toDetailResponseDTO(TestRun testRun, List<TestResult> testResults) {
        UUID projectId = testRun.getProject().getId();
        List<TestResultResponseDTO> results = testResults.stream()
                .map(tr -> toResultResponseDTO(tr, projectId))
                .toList();

        return TestRunDetailResponseDTO.builder()
                .id(testRun.getId())
                .projectId(projectId)
                .title(testRun.getTitle())
                .description(testRun.getDescription())
                .status(testRun.getStatus())
                .testResults(results)
                .createdAt(testRun.getCreatedAt())
                .createdBy(testRun.getCreatedBy())
                .updatedAt(testRun.getUpdatedAt())
                .updatedBy(testRun.getUpdatedBy())
                .build();
    }

    private TestResultResponseDTO toResultResponseDTO(TestResult testResult, UUID projectId) {
        String evidenceUrl = testResult.getEvidence() == null ? null : String.format(
                "/api/projects/%s/test-runs/%s/test-results/%s/evidence",
                projectId, testResult.getTestRun().getId(), testResult.getId()
        );

        return TestResultResponseDTO.builder()
                .id(testResult.getId())
                .testRunId(testResult.getTestRun().getId())
                .testCaseId(testResult.getTestCase().getId())
                .testCaseTitle(testResult.getTestCase().getTitle())
                .testCaseDescription(testResult.getTestCase().getDescription())
                .testCasePrecondition(testResult.getTestCase().getPrecondition())
                // Snapshot saat mapping; baris lama (snapshot NULL) -> test case live.
                .testCaseTestStep(testResult.getTestStepSnapshot() != null
                        ? testResult.getTestStepSnapshot() : testResult.getTestCase().getTestStep())
                .testCaseExpectedResult(testResult.getExpectedResultSnapshot() != null
                        ? testResult.getExpectedResultSnapshot() : testResult.getTestCase().getExpectedResult())
                .testCasePriority(testResult.getTestCase().getPriority())
                .testCaseType(testResult.getTestCase().getType())
                .testCaseScenarioType(testResult.getTestCase().getScenarioType())
                .status(testResult.getStatus())
                .evidenceUrl(evidenceUrl)
                .comment(testResult.getComment())
                .stepResults(parseStepResults(testResult.getStepResults()))
                .version(testResult.getVersion())
                .createdAt(testResult.getCreatedAt())
                .createdBy(testResult.getCreatedBy())
                .updatedAt(testResult.getUpdatedAt())
                .updatedBy(testResult.getUpdatedBy())
                .build();
    }
}
