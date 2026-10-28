// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseAiWorker.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import com.example.app.modules.testcase.ai.InvalidDraftOutputException;
import com.example.app.modules.testcase.ai.ParsedDrafts;
import com.example.app.modules.testcase.ai.TestCaseAiPromptBuilder;
import com.example.app.modules.testcase.ai.TestCaseDraftParser;
import com.example.app.modules.testcase.entity.TestCaseAiGeneration;
import com.example.app.modules.testcase.repository.TestCaseAiGenerationRepository;
import com.example.app.shared.ai.AiCallSpec;
import com.example.app.shared.ai.AiGateway;
import com.example.app.shared.ai.AiMessages;
import com.example.app.shared.ai.AiProviderException;
import com.example.app.shared.ai.AiResponse;
import com.example.app.shared.ai.AiTier;
import com.example.app.shared.ai.AiTierProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mengerjakan SATU job di thread latar belakang. Panggilan ke AI berada DI LUAR transaksi (tiap perubahan status = transaksi
 * singkat sendiri). Teks requirement hanya ada di memori selama job berjalan dan tidak pernah ditulis ke database maupun log;
 * isi prompt dan keluaran AI juga tidak pernah dicatat di log.
 */
@Slf4j
@Component
public class TestCaseAiWorker {

    private final TestCaseAiGenerationRepository repository;
    private final AiGateway aiGateway;
    private final TestCaseAiPromptBuilder promptBuilder;
    private final TestCaseDraftParser parser;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TestCaseAiWorker(TestCaseAiGenerationRepository repository, AiGateway aiGateway, TestCaseAiPromptBuilder promptBuilder,
                            TestCaseDraftParser parser, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.aiGateway = aiGateway;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void run(UUID generationId, String requirement) {
        TestCaseAiGeneration generation = repository.findById(generationId).orElse(null);
        if (generation == null || generation.getStatus() != TestCaseAiGenerationStatus.QUEUED) {
            return; // sudah dikerjakan -- jangan diproses dua kali
        }

        generation.setStatus(TestCaseAiGenerationStatus.RUNNING);
        generation.setStartedAt(now());
        generation = repository.save(generation);

        try {
            execute(generation, requirement);
        } catch (AiProviderException e) {
            // SEMUA kegagalan sisi penyedia AI memberi user pesan umum yang SAMA; bedanya hanya di log server.
            log.error("Generate test case {} gagal di sisi penyedia AI ({}): {}", generationId, e.getKind(), e.getMessage());
            fail(generation, AiMessages.AI_UNAVAILABLE, AiMessages.AI_UNAVAILABLE_MESSAGE);
        } catch (InvalidDraftOutputException e) {
            log.warn("Generate test case {} menghasilkan keluaran AI yang tidak valid: {}", generationId, e.getMessage());
            fail(generation, AiMessages.AI_INVALID_OUTPUT, AiMessages.AI_INVALID_OUTPUT_MESSAGE);
        } catch (Exception e) {
            log.error("Generate test case {} gagal karena kesalahan internal", generationId, e);
            fail(generation, AiMessages.GENERATION_FAILED, AiMessages.GENERATION_FAILED_MESSAGE);
        }
    }

    /** Dipanggil dispatcher saat antrean penuh. */
    public void failQueued(UUID generationId, String errorCode, String message) {
        repository.findById(generationId)
                .filter(g -> g.getStatus() == TestCaseAiGenerationStatus.QUEUED)
                .ifPresent(g -> fail(g, errorCode, message));
    }

    private void execute(TestCaseAiGeneration generation, String requirement) throws JsonProcessingException {
        AiTier tier = AiTier.valueOf(generation.getTier());
        AiTierProperties settings = aiGateway.tier(tier);

        AiCallSpec spec = new AiCallSpec(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(requirement, generation.getRequestedCount(), generation.isIncludeNegative()),
                settings.getTestcaseMaxOutputTokens(),
                Duration.ofSeconds(settings.getTestcaseTimeoutSeconds()),
                true,
                promptBuilder.responseSchema());

        AiResponse response = aiGateway.generate(tier, spec);

        // Catat pemakaian SEBELUM mengurai keluaran: token sudah terpakai walau hasilnya tidak bisa dipakai.
        generation.setModel(response.model());
        generation.setInputTokens(response.inputTokens());
        generation.setOutputTokens(response.outputTokens());
        generation.setPromptVersion(TestCaseAiPromptBuilder.PROMPT_VERSION);

        ParsedDrafts parsed = parser.parse(response.text(), generation.getRequestedCount());
        if (parsed.truncated()) {
            log.warn("Generate test case {}: keluaran terpotong, {} draft lengkap diselamatkan ({} dibuang).",
                    generation.getId(), parsed.drafts().size(), parsed.discarded());
        }

        generation.setDraftsJson(mapper.writeValueAsString(parsed.drafts()));
        generation.setDraftCount(parsed.drafts().size());
        generation.setTruncated(parsed.truncated());
        generation.setStatus(TestCaseAiGenerationStatus.SUCCEEDED);
        generation.setFinishedAt(now());
        repository.save(generation);
    }

    private void fail(TestCaseAiGeneration generation, String errorCode, String message) {
        generation.setStatus(TestCaseAiGenerationStatus.FAILED);
        generation.setErrorCode(errorCode);
        generation.setErrorMessage(message);
        generation.setDraftsJson(null);
        generation.setDraftCount(0);
        generation.setFinishedAt(now());
        repository.save(generation);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
