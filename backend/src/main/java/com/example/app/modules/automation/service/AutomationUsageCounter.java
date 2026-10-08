// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationUsageCounter.java
package com.example.app.modules.automation.service;

import com.example.app.modules.automation.repository.AutomationGenerationRepository;
import com.example.app.shared.ai.AiUsageCounter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Menyumbang hitungan generate Automation ke anggaran global lintas fitur. */
@Component
public class AutomationUsageCounter implements AiUsageCounter {

    private final AutomationGenerationRepository repository;

    public AutomationUsageCounter(AutomationGenerationRepository repository) {
        this.repository = repository;
    }

    @Override
    public long countSince(LocalDateTime since) {
        return repository.countByStatusInAndCreatedAtGreaterThanEqual(AutomationQuotaService.COUNTED, since);
    }
}
