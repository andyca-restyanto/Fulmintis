// filepath: /backend/src/main/java/com/example/app/modules/automation/service/impl/AutomationGenerationServiceImpl.java
package com.example.app.modules.automation.service.impl;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.dto.AutomationFileDTO;
import com.example.app.modules.automation.dto.AutomationGenerationCreatedDTO;
import com.example.app.modules.automation.dto.AutomationGenerationResponseDTO;
import com.example.app.modules.automation.dto.AutomationGenerationSummaryDTO;
import com.example.app.modules.automation.dto.AutomationUsageResponseDTO;
import com.example.app.modules.automation.dto.GenerateAutomationRequestDTO;
import com.example.app.modules.automation.entity.AutomationGeneration;
import com.example.app.modules.automation.entity.AutomationSetup;
import com.example.app.modules.automation.exception.AutomationApiException;
import com.example.app.modules.automation.repository.AutomationGenerationRepository;
import com.example.app.modules.automation.repository.AutomationSetupRepository;
import com.example.app.modules.automation.service.AutomationGenerationService;
import com.example.app.modules.automation.service.AutomationJobDispatcher;
import com.example.app.modules.automation.service.AutomationQuotaService;
import com.example.app.modules.automation.service.AutomationZipBuilder;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.ai.AiGateway;
import com.example.app.shared.ai.AiTier;
import com.example.app.shared.ai.AiTierProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutomationGenerationServiceImpl implements AutomationGenerationService {

    private static final int HISTORY_LIMIT = 20;
    private static final List<AutomationGenerationStatus> ACTIVE = List.of(
            AutomationGenerationStatus.QUEUED, AutomationGenerationStatus.RUNNING);

    private final ProjectAccessService projectAccessService;
    private final AutomationSetupRepository setupRepository;
    private final AutomationGenerationRepository generationRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;
    private final AiGateway aiGateway;
    private final AutomationQuotaService quotaService;
    private final AutomationJobDispatcher dispatcher;
    private final ActivityLogService activityLogService;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public AutomationGenerationCreatedDTO submit(String userEmail, UUID projectId, GenerateAutomationRequestDTO request) {
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);

        AutomationSetup setup = setupRepository.findByProjectId(projectId)
                .orElseThrow(AutomationApiException::setupRequired);

        // AI belum dikonfigurasi, atau provider baru saja melaporkan kuota/saldo habis (circuit terbuka):
        // tolak SEKARANG dgn pesan umum, jangan membuat job yang pasti gagal.
        if (!aiGateway.canAcceptRequests()) {
            throw AutomationApiException.aiUnavailable();
        }

        AiTier tier = tierOf(userEmail);
        AiTierProperties settings = aiGateway.tier(tier);

        LinkedHashSet<UUID> ids = new LinkedHashSet<>(request.getTestCaseIds());
        if (ids.size() > settings.getMaxTestCases()) {
            throw AutomationApiException.tooManyTestCases(settings.getMaxTestCases());
        }
        validateSelection(projectId, ids);

        quotaService.assertCanGenerate(userEmail, tier);
        if (generationRepository.existsByRequestedByAndStatusIn(userEmail, ACTIVE)) {
            throw AutomationApiException.alreadyRunning();
        }

        AutomationGeneration generation = AutomationGeneration.builder()
                .project(myCollaboration.getProject())
                .requestedBy(userEmail)
                .tier(tier.name())
                .status(AutomationGenerationStatus.QUEUED)
                // SNAPSHOT setup: hasil lama tetap bisa ditelusuri walau setup diubah kemudian.
                .framework(setup.getFramework())
                .language(setup.getLanguage())
                .pattern(setup.getPattern())
                .structureNotes(setup.getStructureNotes())
                .testCaseIds(toJson(new ArrayList<>(ids)))
                .testCaseCount(ids.size())
                .build();

        AutomationGeneration saved;
        try {
            // flush: indeks unik parsial "satu job aktif per user" (produksi) memicu pelanggaran di sini kalau
            // dua permintaan lolos pengecekan di atas bersamaan.
            saved = generationRepository.saveAndFlush(generation);
        } catch (DataIntegrityViolationException e) {
            throw AutomationApiException.alreadyRunning();
        }

        activityLogService.log(userEmail, "GENERATE_AUTOMATION");
        dispatcher.dispatch(saved.getId()); // berjalan SETELAH commit

        return AutomationGenerationCreatedDTO.builder().id(saved.getId()).status(saved.getStatus()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutomationGenerationSummaryDTO> listGenerations(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);

        return generationRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, HISTORY_LIMIT))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationGenerationResponseDTO getGeneration(String userEmail, UUID projectId, UUID generationId) {
        projectAccessService.requireMember(userEmail, projectId);
        return toResponse(findOrThrow(generationId, projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationDownload download(String userEmail, UUID projectId, UUID generationId) {
        projectAccessService.requireMember(userEmail, projectId);
        AutomationGeneration generation = findOrThrow(generationId, projectId);

        if (generation.getStatus() != AutomationGenerationStatus.SUCCEEDED) {
            throw AutomationApiException.generationNotReady();
        }

        byte[] zip = AutomationZipBuilder.build(readFiles(generation),
                generation.getFramework().displayName(), generation.getLanguage().displayName());
        String fileName = ("automation-" + generation.getFramework().name() + "-" + generation.getLanguage().name()
                + "-" + generation.getCreatedAt().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip")
                .toLowerCase(Locale.ROOT);
        return new AutomationDownload(fileName, zip);
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationUsageResponseDTO getUsage(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);

        AiTier tier = tierOf(userEmail);
        AiTierProperties settings = aiGateway.tier(tier);
        return AutomationUsageResponseDTO.builder()
                .aiEnabled(aiGateway.isEnabled())
                .tier(tier.name())
                .maxTestCasesPerGeneration(settings.getMaxTestCases())
                .dailyLimit(settings.getDailyLimit())
                .usedToday(quotaService.usedToday(userEmail))
                .build();
    }

    // ---------- helper ----------

    private AiTier tierOf(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User tidak ditemukan: " + userEmail));
        return AiTier.fromUserType(user.getUserType());
    }

    /** Semua id harus test case ACTIVE milik project ini. */
    private void validateSelection(UUID projectId, LinkedHashSet<UUID> ids) {
        Map<UUID, TestCase> found = new HashMap<>();
        testCaseRepository.findAllById(ids).forEach(testCase -> found.put(testCase.getId(), testCase));
        for (UUID id : ids) {
            TestCase testCase = found.get(id);
            if (testCase == null
                    || !testCase.getProject().getId().equals(projectId)
                    || testCase.getStatus() != TestCaseStatus.ACTIVE) {
                throw AutomationApiException.invalidTestCaseSelection();
            }
        }
    }

    private AutomationGeneration findOrThrow(UUID generationId, UUID projectId) {
        return generationRepository.findByIdAndProjectId(generationId, projectId)
                .orElseThrow(AutomationApiException::generationNotFound);
    }

    private List<AutomationFileDTO> readFiles(AutomationGeneration generation) {
        if (generation.getResultFiles() == null) {
            return List.of();
        }
        try {
            return List.of(mapper.readValue(generation.getResultFiles(), AutomationFileDTO[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Hasil generate " + generation.getId() + " rusak.", e);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private AutomationGenerationSummaryDTO toSummary(AutomationGeneration g) {
        return AutomationGenerationSummaryDTO.builder()
                .id(g.getId())
                .status(g.getStatus())
                .framework(g.getFramework())
                .language(g.getLanguage())
                .pattern(g.getPattern())
                .testCaseCount(g.getTestCaseCount())
                .requestedBy(g.getRequestedBy())
                .createdAt(g.getCreatedAt())
                .finishedAt(g.getFinishedAt())
                .errorCode(g.getErrorCode())
                .errorMessage(g.getErrorMessage())
                .build();
    }

    private AutomationGenerationResponseDTO toResponse(AutomationGeneration g) {
        return AutomationGenerationResponseDTO.builder()
                .id(g.getId())
                .status(g.getStatus())
                .framework(g.getFramework())
                .language(g.getLanguage())
                .pattern(g.getPattern())
                .testCaseCount(g.getTestCaseCount())
                .requestedBy(g.getRequestedBy())
                .createdAt(g.getCreatedAt())
                .finishedAt(g.getFinishedAt())
                // File hanya dikirim kalau SUCCEEDED.
                .files(g.getStatus() == AutomationGenerationStatus.SUCCEEDED ? readFiles(g) : List.of())
                .notes(g.getStatus() == AutomationGenerationStatus.SUCCEEDED ? g.getNotes() : null)
                .errorCode(g.getErrorCode())
                .errorMessage(g.getErrorMessage())
                .build();
    }
}
