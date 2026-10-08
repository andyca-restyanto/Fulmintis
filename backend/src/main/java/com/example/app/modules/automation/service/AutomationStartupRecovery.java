// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationStartupRecovery.java
package com.example.app.modules.automation.service;

import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.AutomationMessages;
import com.example.app.modules.automation.repository.AutomationGenerationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Saat start, job yang masih QUEUED/RUNNING pasti milik proses sebelumnya yang sudah mati -- ditandai gagal supaya
 * user tidak menunggu selamanya dan tidak terblokir aturan "satu job aktif per user".
 * CATATAN: mengasumsikan SATU instance backend (sama seperti rate limit & penyimpanan file lokal). Dengan beberapa
 * instance, start instance baru akan ikut menggagalkan job yang sedang berjalan di instance lain.
 */
@Slf4j
@Component
public class AutomationStartupRecovery implements ApplicationRunner {

    private final AutomationGenerationRepository repository;
    private final Clock clock;

    public AutomationStartupRecovery(AutomationGenerationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    /** Dipisah dari run() supaya mudah diuji. */
    public int recover() {
        int failed = repository.failActiveJobs(
                List.of(AutomationGenerationStatus.QUEUED, AutomationGenerationStatus.RUNNING),
                AutomationGenerationStatus.FAILED,
                AutomationMessages.GENERATION_INTERRUPTED,
                AutomationMessages.GENERATION_INTERRUPTED_MESSAGE,
                LocalDateTime.now(clock));
        if (failed > 0) {
            log.warn("{} job generate automation yang terhenti saat server mati ditandai gagal.", failed);
        }
        return failed;
    }
}
