// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseAiQuotaService.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import com.example.app.modules.testcase.repository.TestCaseAiGenerationRepository;
import com.example.app.shared.ai.AiApiException;
import com.example.app.shared.ai.AiGlobalBudget;
import com.example.app.shared.ai.AiProperties;
import com.example.app.shared.ai.AiTier;
import com.example.app.shared.ai.AiTierProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kuota generate TEST CASE: hitungan harian sendiri, TERPISAH dari Automation (tabel berbeda). Yang dihitung: SUCCEEDED + yang sedang
 * QUEUED/RUNNING hari ini. Job gagal karena sisi AI tidak menghabiskan jatah. "Generate ulang" = generate baru = memakan jatah;
 * menyimpan hasil (commit) tidak.
 */
@Service
public class TestCaseAiQuotaService {

    static final List<TestCaseAiGenerationStatus> COUNTED = List.of(
            TestCaseAiGenerationStatus.SUCCEEDED, TestCaseAiGenerationStatus.QUEUED, TestCaseAiGenerationStatus.RUNNING);

    private final TestCaseAiGenerationRepository repository;
    private final AiProperties properties;
    private final AiGlobalBudget globalBudget;

    public TestCaseAiQuotaService(TestCaseAiGenerationRepository repository, AiProperties properties, AiGlobalBudget globalBudget) {
        this.repository = repository;
        this.properties = properties;
        this.globalBudget = globalBudget;
    }

    public int usedToday(String userEmail) {
        return (int) repository.countByRequestedByAndStatusInAndCreatedAtGreaterThanEqual(userEmail, COUNTED, globalBudget.startOfToday());
    }

    /**
     * @throws AiApiException 429 (pesan spesifik) kalau batas harian user tercapai; 503 umum kalau batas global sistem
     *                        (lintas fitur) tercapai -- urusan operasional, bukan salah user.
     */
    public void assertCanGenerate(String userEmail, AiTier tier) {
        AiTierProperties settings = properties.tier(tier);
        if (settings.getTestcaseDailyLimit() > 0 && usedToday(userEmail) >= settings.getTestcaseDailyLimit()) {
            throw AiApiException.dailyLimitReached(settings.getTestcaseDailyLimit());
        }
        if (globalBudget.isExceeded()) {
            throw AiApiException.aiUnavailable();
        }
    }
}
