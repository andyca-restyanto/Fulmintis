// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationGenerationWorker.java
package com.example.app.modules.automation.service;

import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.AutomationMessages;
import com.example.app.modules.automation.ai.AutomationOutputParser;
import com.example.app.modules.automation.ai.AutomationPromptBuilder;
import com.example.app.modules.automation.ai.AutomationTask;
import com.example.app.modules.automation.ai.InvalidAiOutputException;
import com.example.app.modules.automation.ai.ParsedOutput;
import com.example.app.modules.automation.entity.AutomationGeneration;
import com.example.app.modules.automation.repository.AutomationGenerationRepository;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.shared.ai.AiGateway;
import com.example.app.shared.ai.AiProviderException;
import com.example.app.shared.ai.AiResponse;
import com.example.app.shared.ai.AiTier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mengerjakan SATU job di thread latar belakang. Panggilan ke AI (bisa puluhan detik) SENGAJA berada di luar
 * transaksi: tiap perubahan status disimpan lewat repository (transaksi singkat sendiri-sendiri) sehingga
 * koneksi database tidak ditahan selama menunggu AI.
 * <p>
 * Isi prompt dan keluaran AI TIDAK PERNAH ditulis ke log (bisa memuat data sensitif dari test case).
 */
@Slf4j
@Component
public class AutomationGenerationWorker {

    private final AutomationGenerationRepository generationRepository;
    private final TestCaseRepository testCaseRepository;
    private final AiGateway aiGateway;
    private final AutomationPromptBuilder promptBuilder;
    private final AutomationOutputParser outputParser;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AutomationGenerationWorker(
            AutomationGenerationRepository generationRepository,
            TestCaseRepository testCaseRepository,
            AiGateway aiGateway,
            AutomationPromptBuilder promptBuilder,
            AutomationOutputParser outputParser,
            ObjectMapper mapper,
            Clock clock
    ) {
        this.generationRepository = generationRepository;
        this.testCaseRepository = testCaseRepository;
        this.aiGateway = aiGateway;
        this.promptBuilder = promptBuilder;
        this.outputParser = outputParser;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void run(UUID generationId) {
        AutomationGeneration generation = generationRepository.findById(generationId).orElse(null);
        if (generation == null || generation.getStatus() != AutomationGenerationStatus.QUEUED) {
            return; // sudah dikerjakan/dibatalkan -- jangan diproses dua kali
        }

        generation.setStatus(AutomationGenerationStatus.RUNNING);
        generation.setStartedAt(now());
        generation = generationRepository.save(generation);

        try {
            execute(generation);
        } catch (AiProviderException e) {
            // SEMUA jenis kegagalan provider (kuota habis, rate limit, tidak tersedia, timeout, ditolak) memberi
            // user pesan umum yang SAMA; bedanya hanya di log server.
            log.error("Generate automation {} gagal di sisi penyedia AI ({}): {}", generationId, e.getKind(), e.getMessage());
            fail(generation, AutomationMessages.AI_UNAVAILABLE, AutomationMessages.AI_UNAVAILABLE_MESSAGE);
        } catch (InvalidAiOutputException e) {
            log.warn("Generate automation {} menghasilkan keluaran AI yang tidak valid: {}", generationId, e.getMessage());
            fail(generation, AutomationMessages.AI_INVALID_OUTPUT, AutomationMessages.AI_INVALID_OUTPUT_MESSAGE);
        } catch (Exception e) {
            log.error("Generate automation {} gagal karena kesalahan internal", generationId, e);
            fail(generation, AutomationMessages.GENERATION_FAILED, AutomationMessages.GENERATION_FAILED_MESSAGE);
        }
    }

    /** Dipanggil dispatcher saat antrean penuh: job yang belum sempat berjalan ditandai gagal (SERVER_BUSY). */
    public void failQueued(UUID generationId, String errorCode, String message) {
        generationRepository.findById(generationId)
                .filter(g -> g.getStatus() == AutomationGenerationStatus.QUEUED)
                .ifPresent(g -> fail(g, errorCode, message));
    }

    private void execute(AutomationGeneration generation) throws JsonProcessingException {
        List<UUID> ids = List.of(mapper.readValue(generation.getTestCaseIds(), UUID[].class));
        Map<UUID, TestCase> byId = new HashMap<>();
        testCaseRepository.findAllById(ids).forEach(testCase -> byId.put(testCase.getId(), testCase));
        List<TestCase> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        if (ordered.isEmpty()) {
            throw new IllegalStateException("Test case yang dipilih sudah tidak ada.");
        }

        AutomationTask task = promptBuilder.buildTask(generation.getFramework(), generation.getLanguage(),
                generation.getPattern(), generation.getStructureNotes(), ordered);
        String systemPrompt = promptBuilder.systemPrompt(generation.getFramework(), generation.getLanguage(), generation.getPattern());
        String userPrompt = promptBuilder.userPrompt(task);

        AiResponse response = aiGateway.generate(AiTier.valueOf(generation.getTier()), systemPrompt, userPrompt);

        // Catat pemakaian SEBELUM memvalidasi keluaran: token sudah terpakai walau hasilnya tidak bisa dipakai.
        generation.setModel(response.model());
        generation.setInputTokens(response.inputTokens());
        generation.setOutputTokens(response.outputTokens());
        generation.setPromptVersion(AutomationPromptBuilder.PROMPT_VERSION);

        ParsedOutput output = outputParser.parse(response.text());
        if (!output.skippedPaths().isEmpty()) {
            log.info("Generate automation {}: {} berkas dilewati (jenis tidak didukung/ganda): {}",
                    generation.getId(), output.skippedPaths().size(), output.skippedPaths());
        }

        generation.setResultFiles(mapper.writeValueAsString(output.files()));
        generation.setNotes(output.notes());
        generation.setStatus(AutomationGenerationStatus.SUCCEEDED);
        generation.setFinishedAt(now());
        generationRepository.save(generation);
    }

    private void fail(AutomationGeneration generation, String errorCode, String message) {
        generation.setStatus(AutomationGenerationStatus.FAILED);
        generation.setErrorCode(errorCode);
        generation.setErrorMessage(message);
        generation.setResultFiles(null);
        generation.setFinishedAt(now());
        generationRepository.save(generation);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
