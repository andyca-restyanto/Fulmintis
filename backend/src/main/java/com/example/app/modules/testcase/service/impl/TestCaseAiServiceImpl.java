// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/impl/TestCaseAiServiceImpl.java
package com.example.app.modules.testcase.service.impl;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.ai.TestCaseAiPromptBuilder;
import com.example.app.modules.testcase.dto.TestCaseAiCommitItemDTO;
import com.example.app.modules.testcase.dto.TestCaseAiCommitRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseAiCommitResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseAiCreatedDTO;
import com.example.app.modules.testcase.dto.TestCaseAiGenerateRequestDTO;
import com.example.app.modules.testcase.dto.TestCaseAiGenerationResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseAiUsageResponseDTO;
import com.example.app.modules.testcase.dto.TestCaseDraftDTO;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.entity.TestCaseAiGeneration;
import com.example.app.modules.testcase.repository.TestCaseAiGenerationRepository;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testcase.service.TestCaseAiErrors;
import com.example.app.modules.testcase.service.TestCaseAiJobDispatcher;
import com.example.app.modules.testcase.service.TestCaseAiQuotaService;
import com.example.app.modules.testcase.service.TestCaseAiService;
import com.example.app.modules.testrepository.entity.TestFolder;
import com.example.app.modules.testrepository.exception.TestFolderNotFoundException;
import com.example.app.modules.testrepository.repository.TestFolderRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.ai.AiApiException;
import com.example.app.shared.ai.AiGateway;
import com.example.app.shared.ai.AiProperties;
import com.example.app.shared.ai.AiTier;
import com.example.app.shared.ai.AiTierProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestCaseAiServiceImpl implements TestCaseAiService {

    private static final List<TestCaseAiGenerationStatus> ACTIVE = List.of(
            TestCaseAiGenerationStatus.QUEUED, TestCaseAiGenerationStatus.RUNNING);
    private static final int MAX_FIELD_CHARS = 20_000;

    private final ProjectAccessService projectAccessService;
    private final TestCaseAiGenerationRepository generationRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestFolderRepository testFolderRepository;
    private final UserRepository userRepository;
    private final AiGateway aiGateway;
    private final AiProperties aiProperties;
    private final TestCaseAiQuotaService quotaService;
    private final TestCaseAiJobDispatcher dispatcher;
    private final ActivityLogService activityLogService;
    private final ObjectMapper mapper;
    private final Clock clock;

    // ---------- usage ----------

    @Override
    @Transactional(readOnly = true)
    public TestCaseAiUsageResponseDTO getUsage(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);

        AiTier tier = tierOf(userEmail);
        AiTierProperties settings = aiGateway.tier(tier);
        return TestCaseAiUsageResponseDTO.builder()
                .aiEnabled(aiGateway.isEnabled())
                .tier(tier.name())
                .maxDraftsPerGeneration(settings.getTestcaseMaxDrafts())
                .maxRequirementChars(aiProperties.getTestcase().getMaxRequirementChars())
                .dailyLimit(settings.getTestcaseDailyLimit())
                .usedToday(quotaService.usedToday(userEmail))
                .sandbox(aiProperties.isSandbox())
                .build();
    }

    // ---------- generate ----------

    @Override
    @Transactional
    public TestCaseAiCreatedDTO submit(String userEmail, UUID projectId, TestCaseAiGenerateRequestDTO request) {
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        AiTier tier = tierOf(userEmail);
        AiTierProperties settings = aiGateway.tier(tier);

        String requirement = request.getRequirement() == null ? "" : request.getRequirement().strip();
        if (requirement.isEmpty()) {
            throw TestCaseAiErrors.invalidRequest("Requirement wajib diisi.");
        }
        int maxChars = aiProperties.getTestcase().getMaxRequirementChars();
        if (requirement.length() > maxChars) {
            throw TestCaseAiErrors.requirementTooLong(maxChars);
        }
        int count = request.getCount() == null ? settings.getTestcaseMaxDrafts() : request.getCount();
        if (count < 1) {
            throw TestCaseAiErrors.invalidRequest("Jumlah draft minimal 1.");
        }
        if (count > settings.getTestcaseMaxDrafts()) {
            throw TestCaseAiErrors.tooManyDrafts(settings.getTestcaseMaxDrafts());
        }

        // Folder wajib ada & milik project ini (404 kalau tidak) -- sama seperti membuat test case.
        findFolderOrThrow(request.getFolderId(), projectId);

        // AI belum dikonfigurasi, atau provider baru saja melaporkan kuota habis (circuit terbuka): tolak SEKARANG dgn pesan umum.
        if (!aiGateway.canAcceptRequests()) {
            throw AiApiException.aiUnavailable();
        }
        quotaService.assertCanGenerate(userEmail, tier);
        if (generationRepository.existsByRequestedByAndStatusIn(userEmail, ACTIVE)) {
            throw AiApiException.alreadyRunning();
        }

        // Pembersihan draft kedaluwarsa dilakukan sambil lalu (murah); pembersihan utama saat startup.
        generationRepository.clearExpiredDrafts(LocalDateTime.now(clock).minusDays(aiProperties.getTestcase().getDraftRetentionDays()));

        TestCaseAiGeneration generation = TestCaseAiGeneration.builder()
                .project(myCollaboration.getProject())
                .folderId(request.getFolderId())
                .requestedBy(userEmail)
                .tier(tier.name())
                .status(TestCaseAiGenerationStatus.QUEUED)
                .requestedCount(count)
                .includeNegative(request.getIncludeNegative() == null || request.getIncludeNegative())
                // Hanya PANJANG requirement yang disimpan, bukan isinya.
                .requirementLength(requirement.length())
                .build();

        TestCaseAiGeneration saved;
        try {
            // flush: indeks unik parsial "satu job aktif per user" (produksi) memicu pelanggaran di sini kalau dua permintaan
            // lolos pengecekan di atas bersamaan.
            saved = generationRepository.saveAndFlush(generation);
        } catch (DataIntegrityViolationException e) {
            throw AiApiException.alreadyRunning();
        }

        activityLogService.log(userEmail, "GENERATE_TEST_CASES_AI");
        dispatcher.dispatch(saved.getId(), requirement); // requirement lewat memori, setelah commit

        return TestCaseAiCreatedDTO.builder().id(saved.getId()).status(saved.getStatus()).build();
    }

    // ---------- baca ----------

    @Override
    @Transactional(readOnly = true)
    public TestCaseAiGenerationResponseDTO getGeneration(String userEmail, UUID projectId, UUID generationId) {
        projectAccessService.requireMember(userEmail, projectId);
        return toResponse(findOwnOrThrow(generationId, projectId, userEmail));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestCaseAiGenerationResponseDTO> getPending(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);
        return generationRepository
                .findFirstByProjectIdAndRequestedByAndStatusAndCommittedFalseAndDraftsJsonIsNotNullOrderByCreatedAtDesc(
                        projectId, userEmail, TestCaseAiGenerationStatus.SUCCEEDED)
                .map(this::toResponse);
    }

    // ---------- commit ----------

    @Override
    @Transactional
    public TestCaseAiCommitResponseDTO commit(String userEmail, UUID projectId, UUID generationId, TestCaseAiCommitRequestDTO request) {
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);
        TestCaseAiGeneration generation = findOwnOrThrow(generationId, projectId, userEmail);

        if (generation.isCommitted()) {
            throw TestCaseAiErrors.alreadyCommitted();
        }
        if (generation.getStatus() != TestCaseAiGenerationStatus.SUCCEEDED) {
            throw TestCaseAiErrors.notReady();
        }
        if (generation.getDraftsJson() == null) {
            throw TestCaseAiErrors.draftExpired();
        }

        UUID folderId = request.getFolderId() == null ? generation.getFolderId() : request.getFolderId();
        TestFolder folder = findFolderOrThrow(folderId, projectId);

        List<TestCaseAiCommitItemDTO> items = request.getTestCases() == null ? List.of() : request.getTestCases();
        if (items.isEmpty()) {
            throw TestCaseAiErrors.noDraftsSelected();
        }
        // Batas jumlah mengikuti tier SAAT generate (yang tersimpan di baris), bukan tier user saat ini.
        int maxItems = aiGateway.tier(AiTier.valueOf(generation.getTier())).getTestcaseMaxDrafts();
        if (items.size() > maxItems) {
            throw TestCaseAiErrors.tooManyDrafts(maxItems);
        }
        List<Map<String, Object>> errors = validateItems(items);
        if (!errors.isEmpty()) {
            throw TestCaseAiErrors.invalidDrafts(errors);
        }

        // Klaim atomik: hanya SATU permintaan commit yang lolos; sisanya (klik ganda, tab kedua) ditolak 409 dan TIDAK menyimpan apa pun.
        int claimed = generationRepository.claimForCommit(generationId, LocalDateTime.now(clock), items.size(), TestCaseAiGenerationStatus.SUCCEEDED);
        if (claimed == 0) {
            boolean alreadyCommitted = generationRepository.findById(generationId).map(TestCaseAiGeneration::isCommitted).orElse(false);
            throw alreadyCommitted ? TestCaseAiErrors.alreadyCommitted() : TestCaseAiErrors.draftExpired();
        }

        Project project = myCollaboration.getProject();
        List<TestCase> entities = new ArrayList<>(items.size());
        for (TestCaseAiCommitItemDTO item : items) {
            entities.add(TestCase.builder()
                    .project(project)
                    .folder(folder)
                    .title(item.getTitle().strip())
                    .priority(item.getPriority())
                    .type(item.getType())
                    .scenarioType(item.getScenarioType())
                    .description(blankToNull(item.getDescription()))
                    .objective(blankToNull(item.getObjective()))
                    .precondition(blankToNull(item.getPrecondition()))
                    .testStep(blankToNull(item.getTestStep()))
                    .expectedResult(blankToNull(item.getExpectedResult()))
                    .createdBy(userEmail)
                    .updatedBy(userEmail)
                    .build());
        }
        testCaseRepository.saveAll(entities); // satu transaksi dgn klaim di atas: gagal di sini = klaim ikut dibatalkan

        activityLogService.log(userEmail, "COMMIT_AI_TEST_CASES");
        return TestCaseAiCommitResponseDTO.builder()
                .savedCount(entities.size())
                .folderId(folder.getId())
                .folderName(folder.getFolderName())
                .build();
    }

    // ---------- helper ----------

    /** Aturan yang sama dgn membuat test case manual (CreateTestCaseRequestDTO), plus batas panjang; error membawa INDEKS item. */
    private List<Map<String, Object>> validateItems(List<TestCaseAiCommitItemDTO> items) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            TestCaseAiCommitItemDTO item = items.get(index);
            if (item == null) {
                errors.add(error(index, "item", "Draft tidak boleh kosong"));
                continue;
            }
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                errors.add(error(index, "title", "Title wajib diisi"));
            } else if (item.getTitle().strip().length() > 255) {
                errors.add(error(index, "title", "Title maksimal 255 karakter"));
            }
            if (item.getPriority() == null) {
                errors.add(error(index, "priority", "Priority wajib dipilih"));
            }
            if (item.getType() == null) {
                errors.add(error(index, "type", "Type wajib dipilih"));
            }
            checkLength(errors, index, "description", item.getDescription());
            checkLength(errors, index, "objective", item.getObjective());
            checkLength(errors, index, "precondition", item.getPrecondition());
            checkLength(errors, index, "testStep", item.getTestStep());
            checkLength(errors, index, "expectedResult", item.getExpectedResult());
        }
        return errors;
    }

    private static void checkLength(List<Map<String, Object>> errors, int index, String field, String value) {
        if (value != null && value.length() > MAX_FIELD_CHARS) {
            errors.add(error(index, field, field + " maksimal " + MAX_FIELD_CHARS + " karakter"));
        }
    }

    private static Map<String, Object> error(int index, String field, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("index", index);
        error.put("field", field);
        error.put("message", message);
        return error;
    }

    private AiTier tierOf(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User tidak ditemukan: " + userEmail));
        return AiTier.fromUserType(user.getUserType());
    }

    private TestFolder findFolderOrThrow(UUID folderId, UUID projectId) {
        return testFolderRepository.findById(folderId)
                .filter(folder -> folder.getProject().getId().equals(projectId))
                .orElseThrow(TestFolderNotFoundException::new);
    }

    /** Draft pribadi: milik user lain (atau project lain) dibalas 404, tidak 403 -- tidak membocorkan keberadaannya. */
    private TestCaseAiGeneration findOwnOrThrow(UUID generationId, UUID projectId, String userEmail) {
        return generationRepository.findByIdAndProjectIdAndRequestedBy(generationId, projectId, userEmail)
                .orElseThrow(TestCaseAiErrors::notFound);
    }

    private TestCaseAiGenerationResponseDTO toResponse(TestCaseAiGeneration g) {
        boolean showDrafts = g.getStatus() == TestCaseAiGenerationStatus.SUCCEEDED && !g.isCommitted() && g.getDraftsJson() != null;
        List<TestCaseDraftDTO> drafts = showDrafts ? markDuplicates(readDrafts(g), g.getFolderId()) : List.of();
        String folderName = testFolderRepository.findById(g.getFolderId()).map(TestFolder::getFolderName).orElse(null);

        return TestCaseAiGenerationResponseDTO.builder()
                .id(g.getId())
                .status(g.getStatus())
                .folderId(g.getFolderId())
                .folderName(folderName)
                .requestedCount(g.getRequestedCount())
                .draftCount(g.getDraftCount())
                .truncated(g.isTruncated())
                .committed(g.isCommitted())
                .createdAt(g.getCreatedAt())
                .finishedAt(g.getFinishedAt())
                .drafts(drafts)
                .errorCode(g.getErrorCode())
                .errorMessage(g.getErrorMessage())
                .build();
    }

    private List<TestCaseDraftDTO> readDrafts(TestCaseAiGeneration g) {
        try {
            return List.of(mapper.readValue(g.getDraftsJson(), TestCaseDraftDTO[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Draft " + g.getId() + " rusak.", e);
        }
    }

    /** Judul sama (tanpa membedakan huruf besar/kecil) dgn test case aktif di folder tujuan -> ditandai, tidak memblokir. */
    private List<TestCaseDraftDTO> markDuplicates(List<TestCaseDraftDTO> drafts, UUID folderId) {
        Set<String> existing = new HashSet<>(testCaseRepository.findLowerTitlesByFolderIdAndStatus(folderId, TestCaseStatus.ACTIVE));
        List<TestCaseDraftDTO> marked = new ArrayList<>(drafts.size());
        for (TestCaseDraftDTO draft : drafts) {
            marked.add(draft.withDuplicate(existing.contains(draft.title().toLowerCase(java.util.Locale.ROOT).strip())));
        }
        return marked;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
