// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseAiUsageCounter.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.repository.TestCaseAiGenerationRepository;
import com.example.app.shared.ai.AiUsageCounter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Menyumbang hitungan generate test case ke anggaran global lintas fitur. */
@Component
public class TestCaseAiUsageCounter implements AiUsageCounter {

    private final TestCaseAiGenerationRepository repository;

    public TestCaseAiUsageCounter(TestCaseAiGenerationRepository repository) {
        this.repository = repository;
    }

    @Override
    public long countSince(LocalDateTime since) {
        return repository.countByStatusInAndCreatedAtGreaterThanEqual(TestCaseAiQuotaService.COUNTED, since);
    }
}
