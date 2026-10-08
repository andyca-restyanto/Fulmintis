// backend/src/main/java/com/example/app/modules/testcase/service/impl/TestCaseServiceImpl.java
package com.example.app.modules.testcase.service.impl;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.exception.ProjectAccessForbiddenException;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.projectteam.ProjectTeamCode;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.dto.CreateTestCaseRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseSearchCriteria;
import com.example.app.modules.testcase.dto.UpdateTestCaseRequestDTO;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.exception.TestCaseAlreadyArchivedException;
import com.example.app.modules.testcase.exception.TestCaseNotFoundException;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testcase.service.TestCaseService;
import com.example.app.modules.testcase.specification.TestCaseSpecifications;
import com.example.app.modules.testrepository.entity.TestFolder;
import com.example.app.modules.testrepository.exception.TestFolderNotFoundException;
import com.example.app.modules.testrepository.repository.TestFolderRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestCaseServiceImpl implements TestCaseService {

    private final TestCaseRepository testCaseRepository;                        // -> datasource "frontline"
    private final TestFolderRepository testFolderRepository;                     // -> datasource "frontline"
    private final ProjectAccessService projectAccessService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public TestCaseResponseDTO createTestCase(String userEmail, UUID projectId, CreateTestCaseRequestDTO request) {
        // Requirement #4: OWNER MAUPUN COLLABORATOR boleh create test case
        // -- karena cuma 2 role itu yang ada di sistem ini (ProjectTeamCode
        // hanya OWNER/COLLABORATOR), lolos cek membership di bawah ini
        // SUDAH otomatis memenuhi requirement #4, tidak perlu cek role lagi
        // (beda dgn create test folder yang dibatasi OWNER saja).
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        Project project = myCollaboration.getProject();

        // Requirement #2: folder WAJIB sudah ada & di project yang sama --
        // reuse exception dari modul testrepository (anti-enumeration,
        // sama seperti validasi parent folder di TestFolderServiceImpl).
        TestFolder folder = findFolderOrThrow(request.getFolderId(), projectId);

        TestCase testCase = TestCase.builder()
                .project(project)
                .folder(folder)
                .title(request.getTitle().trim())
                .priority(request.getPriority())
                .type(request.getType())
                .scenarioType(request.getScenarioType())
                .description(blankToNull(request.getDescription()))
                .objective(blankToNull(request.getObjective()))
                .precondition(blankToNull(request.getPrecondition()))
                .testStep(blankToNull(request.getTestStep()))
                .expectedResult(blankToNull(request.getExpectedResult()))
                // status default ACTIVE via @Builder.Default di entity -- tidak
                // perlu di-set manual di sini.
                .createdBy(userEmail)
                .updatedBy(userEmail)
                .build();

        TestCase savedTestCase = testCaseRepository.save(testCase);

        activityLogService.log(userEmail, "CREATE_TEST_CASE");

        return toResponseDTO(savedTestCase);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestCaseResponseDTO> searchTestCases(String userEmail, UUID projectId, TestCaseSearchCriteria criteria) {
        // Search/filter (requirement #6) boleh OWNER maupun COLLABORATOR --
        // cukup validasi membership, sama seperti createTestCase().
        projectAccessService.requireMember(userEmail, projectId);

        // Requirement #4: listing "biasa" ini SELALU dipaksa status=ACTIVE,
        // apapun role-nya (OWNER maupun COLLABORATOR) -- test case archived
        // TIDAK PERNAH nyelip ke sini. OWNER yang mau lihat test case
        // archived wajib lewat listArchivedTestCases() (endpoint terpisah).
        List<TestCase> testCases = findByCriteria(projectId, criteria, TestCaseStatus.ACTIVE);

        return testCases.stream().map(this::toResponseDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TestCaseResponseDTO getTestCaseById(String userEmail, UUID projectId, UUID testCaseId) {
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        TestCase testCase = findTestCaseOrThrow(testCaseId, projectId);

        // Requirement #4: test case ARCHIVED cuma boleh dilihat OWNER.
        // COLLABORATOR yang coba akses (baik lewat search -- yg memang tidak
        // mungkin muncul di sana -- maupun langsung tembak by id) ditolak
        // dgn 404 yang SAMA seperti "id tidak ada", bukan 403, supaya
        // COLLABORATOR tidak bisa membedakan "test case ini tidak ada" vs
        // "test case ini ada tapi di-archive" (anti-enumeration).
        assertArchivedVisibleToCaller(testCase, myCollaboration);

        return toResponseDTO(testCase);
    }

    @Override
    @Transactional
    public TestCaseResponseDTO updateTestCase(
            String userEmail, UUID projectId, UUID testCaseId, UpdateTestCaseRequestDTO request
    ) {
        // Requirement #1: edit boleh OWNER maupun COLLABORATOR -- sama
        // seperti aturan create test case (requirement #4), cukup validasi
        // membership project.
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        TestCase testCase = findTestCaseOrThrow(testCaseId, projectId);

        // Requirement #4: COLLABORATOR tidak boleh lihat test case archived
        // sama sekali -- termasuk tidak boleh "edit sesuatu yang katanya
        // tidak ada" itu. OWNER tetap boleh edit test case archived-nya
        // sendiri (mis. perbaiki data sebelum nanti direstore -- tidak ada
        // pembatasan tambahan di requirement).
        assertArchivedVisibleToCaller(testCase, myCollaboration);

        // folder tujuan boleh sama boleh beda (pindah folder), WAJIB tetap
        // folder yang valid di project yang sama -- validasi sama seperti
        // create.
        TestFolder folder = findFolderOrThrow(request.getFolderId(), projectId);

        testCase.setFolder(folder);
        testCase.setTitle(request.getTitle().trim());
        testCase.setPriority(request.getPriority());
        testCase.setType(request.getType());
        testCase.setScenarioType(request.getScenarioType());
        testCase.setDescription(blankToNull(request.getDescription()));
        testCase.setObjective(blankToNull(request.getObjective()));
        testCase.setPrecondition(blankToNull(request.getPrecondition()));
        testCase.setTestStep(blankToNull(request.getTestStep()));
        testCase.setExpectedResult(blankToNull(request.getExpectedResult()));
        testCase.setUpdatedBy(userEmail);

        TestCase savedTestCase = testCaseRepository.save(testCase);

        activityLogService.log(userEmail, "UPDATE_TEST_CASE");

        return toResponseDTO(savedTestCase);
    }

    @Override
    @Transactional
    public void archiveTestCase(String userEmail, UUID projectId, UUID testCaseId) {
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        // Requirement #3: delete (archive) test case HANYA boleh OWNER
        // project -- 403 (bukan 404) karena user di titik ini sudah
        // terbukti member yang sah, cuma kurang izin. Pola sama dengan
        // TestFolderServiceImpl.createFolder().
        if (!ProjectTeamCode.OWNER.equals(myCollaboration.getProjectTeam())) {
            throw new ProjectAccessForbiddenException(
                    "Hanya OWNER project yang bisa menghapus (archive) test case."
            );
        }

        TestCase testCase = findTestCaseOrThrow(testCaseId, projectId);

        if (testCase.getStatus() == TestCaseStatus.ARCHIVED) {
            throw new TestCaseAlreadyArchivedException();
        }

        // Requirement #2: SOFT DELETE -- flag status jadi ARCHIVED + catat
        // kapan/siapa yang men-delete, row TIDAK dihapus dari database.
        testCase.setStatus(TestCaseStatus.ARCHIVED);
        testCase.setArchivedAt(LocalDateTime.now());
        testCase.setArchivedBy(userEmail);
        testCase.setUpdatedBy(userEmail);

        testCaseRepository.save(testCase);

        activityLogService.log(userEmail, "ARCHIVE_TEST_CASE");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestCaseResponseDTO> listArchivedTestCases(String userEmail, UUID projectId, TestCaseSearchCriteria criteria) {
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        // Requirement #4: listing test case archived HANYA boleh OWNER.
        // 403 (bukan 404) -- beda dgn getTestCaseById/updateTestCase (yang
        // menyembunyikan KEBERADAAN 1 test case tertentu dari COLLABORATOR),
        // di sini COLLABORATOR memang boleh tahu endpoint-nya ada, cuma
        // ditolak aksesnya -- sama seperti createFolder() yg OWNER-only.
        if (!ProjectTeamCode.OWNER.equals(myCollaboration.getProjectTeam())) {
            throw new ProjectAccessForbiddenException(
                    "Hanya OWNER project yang bisa melihat test case yang sudah di-archive."
            );
        }

        List<TestCase> testCases = findByCriteria(projectId, criteria, TestCaseStatus.ARCHIVED);

        return testCases.stream().map(this::toResponseDTO).toList();
    }

    // ---- helper privat ----

    private TestFolder findFolderOrThrow(UUID folderId, UUID projectId) {
        return testFolderRepository.findById(folderId)
                .filter(f -> f.getProject().getId().equals(projectId))
                .orElseThrow(TestFolderNotFoundException::new);
    }

    private TestCase findTestCaseOrThrow(UUID testCaseId, UUID projectId) {
        return testCaseRepository.findById(testCaseId)
                .filter(tc -> tc.getProject().getId().equals(projectId))
                .orElseThrow(TestCaseNotFoundException::new);
    }

    // Requirement #4 -- satu-satunya tempat aturan "archived cuma boleh
    // dilihat OWNER" ditegakkan utk akses per-1-test-case (getTestCaseById
    // & updateTestCase). Lempar TestCaseNotFoundException (BUKAN
    // ProjectAccessForbiddenException) supaya COLLABORATOR tidak bisa
    // membedakan test case yang memang tidak ada vs yang di-archive.
    private void assertArchivedVisibleToCaller(TestCase testCase, ProjectCollaboration callerCollaboration) {
        boolean isArchived = testCase.getStatus() == TestCaseStatus.ARCHIVED;
        boolean callerIsOwner = ProjectTeamCode.OWNER.equals(callerCollaboration.getProjectTeam());

        if (isArchived && !callerIsOwner) {
            throw new TestCaseNotFoundException();
        }
    }

    private List<TestCase> findByCriteria(UUID projectId, TestCaseSearchCriteria criteria, TestCaseStatus status) {
        Specification<TestCase> specification = Specification
                .where(TestCaseSpecifications.hasProjectId(projectId))
                .and(TestCaseSpecifications.hasStatus(status))
                .and(TestCaseSpecifications.hasFolderId(criteria.folderId()))
                .and(TestCaseSpecifications.titleOrIdContains(criteria.keyword()))
                .and(TestCaseSpecifications.hasPriority(criteria.priority()))
                .and(TestCaseSpecifications.hasType(criteria.type()))
                .and(TestCaseSpecifications.hasScenarioType(criteria.scenarioType()));

        return testCaseRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TestCaseResponseDTO toResponseDTO(TestCase testCase) {
        return TestCaseResponseDTO.builder()
                .id(testCase.getId())
                .projectId(testCase.getProject().getId())
                .folderId(testCase.getFolder().getId())
                .title(testCase.getTitle())
                .priority(testCase.getPriority())
                .type(testCase.getType())
                .scenarioType(testCase.getScenarioType())
                .description(testCase.getDescription())
                .objective(testCase.getObjective())
                .precondition(testCase.getPrecondition())
                .testStep(testCase.getTestStep())
                .expectedResult(testCase.getExpectedResult())
                .status(testCase.getStatus())
                .archivedAt(testCase.getArchivedAt())
                .archivedBy(testCase.getArchivedBy())
                .createdAt(testCase.getCreatedAt())
                .createdBy(testCase.getCreatedBy())
                .updatedAt(testCase.getUpdatedAt())
                .updatedBy(testCase.getUpdatedBy())
                .build();
    }
}
