// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseAiStartupRecovery.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import com.example.app.modules.testcase.repository.TestCaseAiGenerationRepository;
import com.example.app.shared.ai.AiMessages;
import com.example.app.shared.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Saat start: (1) job QUEUED/RUNNING milik proses lama ditandai gagal supaya user tidak menunggu selamanya dan tidak terblokir aturan
 * "satu job aktif per user" (requirement-nya hanya ada di memori proses lama, jadi job itu memang tidak bisa dilanjutkan); (2) draft
 * yang tidak pernah disimpan dan sudah melewati masa simpan dikosongkan. Mengasumsikan SATU instance backend.
 */
@Slf4j
@Component
public class TestCaseAiStartupRecovery implements ApplicationRunner {

    private final TestCaseAiGenerationRepository repository;
    private final AiProperties properties;
    private final Clock clock;

    public TestCaseAiStartupRecovery(TestCaseAiGenerationRepository repository, AiProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    /** Dipisah dari run() supaya mudah diuji. */
    public int recover() {
        LocalDateTime now = LocalDateTime.now(clock);
        int failed = repository.failActiveJobs(
                List.of(TestCaseAiGenerationStatus.QUEUED, TestCaseAiGenerationStatus.RUNNING),
                TestCaseAiGenerationStatus.FAILED,
                AiMessages.GENERATION_INTERRUPTED,
                AiMessages.GENERATION_INTERRUPTED_MESSAGE,
                now);
        if (failed > 0) {
            log.warn("{} job generate test case yang terhenti saat server mati ditandai gagal.", failed);
        }
        int purged = repository.clearExpiredDrafts(now.minusDays(properties.getTestcase().getDraftRetentionDays()));
        if (purged > 0) {
            log.info("{} draft test case yang tidak pernah disimpan dibersihkan (lewat masa simpan).", purged);
        }
        return failed;
    }
}
