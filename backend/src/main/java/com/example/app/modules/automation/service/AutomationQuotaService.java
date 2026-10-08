// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationQuotaService.java
package com.example.app.modules.automation.service;

import com.example.app.modules.automation.AutomationGenerationStatus;
import com.example.app.modules.automation.exception.AutomationApiException;
import com.example.app.modules.automation.repository.AutomationGenerationRepository;
import com.example.app.shared.ai.AiGlobalBudget;
import com.example.app.shared.ai.AiProperties;
import com.example.app.shared.ai.AiTier;
import com.example.app.shared.ai.AiTierProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kuota generate. Yang dihitung ke jatah: job SUCCEEDED + yang sedang QUEUED/RUNNING hari ini.
 * Job yang gagal (provider bermasalah, keluaran tidak valid) TIDAK menghabiskan jatah user.
 * Hari = tanggal di zona waktu server (sama dgn grafik timeline dashboard).
 */
@Slf4j
@Service
public class AutomationQuotaService {

    static final List<AutomationGenerationStatus> COUNTED = List.of(
            AutomationGenerationStatus.SUCCEEDED, AutomationGenerationStatus.QUEUED, AutomationGenerationStatus.RUNNING);

    private final AutomationGenerationRepository repository;
    private final AiProperties properties;
    private final AiGlobalBudget globalBudget;
    private final Clock clock;

    public AutomationQuotaService(AutomationGenerationRepository repository, AiProperties properties,
                                  AiGlobalBudget globalBudget, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.globalBudget = globalBudget;
        this.clock = clock;
    }

    public int usedToday(String userEmail) {
        return (int) repository.countByRequestedByAndStatusInAndCreatedAtGreaterThanEqual(userEmail, COUNTED, startOfToday());
    }

    /**
     * @throws AutomationApiException 429 (pesan spesifik, bisa ditindaklanjuti user) kalau batas harian user tercapai;
     *                                503 umum kalau batas global sistem tercapai (urusan operasional, bukan user).
     */
    public void assertCanGenerate(String userEmail, AiTier tier) {
        AiTierProperties settings = properties.tier(tier);
        if (settings.getDailyLimit() > 0 && usedToday(userEmail) >= settings.getDailyLimit()) {
            throw AutomationApiException.dailyLimitReached(settings.getDailyLimit());
        }

        // Batas global dihitung LINTAS fitur AI (Automation + generate test case), lihat AiGlobalBudget.
        if (globalBudget.isExceeded()) {
            throw AutomationApiException.aiUnavailable();
        }
    }

    private LocalDateTime startOfToday() {
        return LocalDate.now(clock).atStartOfDay();
    }
}
