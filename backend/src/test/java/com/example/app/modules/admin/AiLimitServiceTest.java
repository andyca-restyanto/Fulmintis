// filepath: /backend/src/test/java/com/example/app/modules/admin/AiLimitServiceTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.AiLimitsDTO;
import com.example.app.modules.admin.dto.AiLimitsUpdateRequestDTO;
import com.example.app.modules.admin.entity.AiLimitSetting;
import com.example.app.modules.admin.exception.InvalidAiLimitException;
import com.example.app.modules.admin.repository.AiLimitSettingRepository;
import com.example.app.modules.admin.service.impl.AiLimitServiceImpl;
import com.example.app.modules.admin.service.impl.AiLimitStoreImpl;
import com.example.app.modules.automation.AutomationTestSupport.MutableClock;
import com.example.app.shared.activitylog.ActivityLogService;
import com.example.app.shared.ai.AiLimitProvider;
import com.example.app.shared.ai.AiProperties;
import com.example.app.shared.ai.AiTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Dilewati sementara atas permintaan. Hapus baris @Disabled di bawah untuk mengaktifkan kembali.
@Disabled("Dilewati sementara: AiLimitServiceTest")
class AiLimitServiceTest {

    private final Map<String, AiLimitSetting> table = new TreeMap<>();
    private final AiProperties props = new AiProperties();
    private ActivityLogService activityLog;
    private AiLimitProvider provider;
    private AiLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        AiLimitSettingRepository repo = mock(AiLimitSettingRepository.class);
        when(repo.findAll()).thenAnswer(i -> new ArrayList<>(table.values()));
        when(repo.save(any(AiLimitSetting.class))).thenAnswer(i -> {
            AiLimitSetting s = i.getArgument(0);
            table.put(s.getKey(), s);
            return s;
        });
        when(repo.existsById(anyString())).thenAnswer(i -> table.containsKey(i.<String>getArgument(0)));
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(table.get(i.<String>getArgument(0))));
        doAnswer(i -> {
            table.remove(i.<String>getArgument(0));
            return null;
        }).when(repo).deleteById(anyString());

        MutableClock clock = new MutableClock("2026-10-28T01:00:00Z");
        provider = new AiLimitProvider(props, new AiLimitStoreImpl(repo), clock);
        activityLog = mock(ActivityLogService.class);
        service = new AiLimitServiceImpl(repo, provider, activityLog, clock);
    }

    private static AiLimitsUpdateRequestDTO requestFrom(AiLimitsDTO current) {
        AiLimitsUpdateRequestDTO r = new AiLimitsUpdateRequestDTO();
        r.setGlobal(current.getGlobal());
        r.setTiers(current.getTiers());
        return r;
    }

    @Test
    void getReturnsEffectiveValuesDefaultsAndSourcesForThreeTiers() {
        AiLimitsDTO dto = service.get();

        assertEquals(List.of("FREE", "VIP_MONTHLY", "VIP_YEARLY"), List.copyOf(dto.getTiers().keySet()));
        assertEquals(3, dto.getTiers().get("FREE").getAutomation().getDailyLimit());
        assertEquals(30, dto.getTiers().get("VIP_YEARLY").getAutomation().getDailyLimit());
        assertEquals(15, dto.getTiers().get("VIP_MONTHLY").getTestcase().getDailyLimit());
        assertEquals(dto.getTiers().get("FREE").getTestcase().getMaxDrafts(), dto.getDefaults().getTiers().get("FREE").getTestcase().getMaxDrafts());
        assertEquals(0, dto.getGlobal().getDailyLimit());
        assertEquals(3 * 8 + 2, dto.getSource().size());
        assertTrue(dto.getSource().values().stream().allMatch("DEFAULT"::equals));
    }

    @Test
    void updateChangesOnlyTheDifferentValuesAndVipTiersAreIndependent() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getTiers().get("VIP_YEARLY").getAutomation().setDailyLimit(100);

        AiLimitsDTO result = service.update("admin@x.id", request);

        assertEquals(1, table.size());
        AiLimitSetting row = table.get("VIP_YEARLY.AUTOMATION_DAILY_LIMIT");
        assertEquals(100, row.getValue());
        assertEquals("admin@x.id", row.getUpdatedBy());
        assertEquals(100, result.getTiers().get("VIP_YEARLY").getAutomation().getDailyLimit());
        assertEquals(30, result.getTiers().get("VIP_MONTHLY").getAutomation().getDailyLimit());
        assertEquals("DATABASE", result.getSource().get("VIP_YEARLY.AUTOMATION_DAILY_LIMIT"));
        assertEquals("DEFAULT", result.getSource().get("VIP_MONTHLY.AUTOMATION_DAILY_LIMIT"));
        // berlaku langsung di provider yang dipakai quota/gateway, tanpa menunggu cache
        assertEquals(100, provider.tier(AiTier.VIP_YEARLY).getDailyLimit());
        assertEquals(30, provider.tier(AiTier.VIP_MONTHLY).getDailyLimit());
        verify(activityLog).log("admin@x.id", "AI_LIMIT_CHANGED:VIP_YEARLY.AUTOMATION_DAILY_LIMIT:30->100");
    }

    @Test
    void savingUnchangedValuesWritesNothing() {
        service.update("admin@x.id", requestFrom(service.get()));

        assertTrue(table.isEmpty());
        verify(activityLog, never()).log(anyString(), anyString());
    }

    @Test
    void anyInvalidValueRejectsTheWholeRequestWithNoPartialSave() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getTiers().get("FREE").getAutomation().setDailyLimit(5);          // valid
        request.getTiers().get("VIP_YEARLY").getTestcase().setTimeoutSeconds(5);   // di bawah min 10

        InvalidAiLimitException e = assertThrows(InvalidAiLimitException.class, () -> service.update("admin@x.id", request));

        assertTrue(e.getMessage().contains("antara 10 dan 600"), e.getMessage());
        assertTrue(table.isEmpty());
        verify(activityLog, never()).log(anyString(), anyString());
    }

    @Test
    void negativeAndHugeValuesAreRejected() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getGlobal().setDailyLimit(-1);
        assertThrows(InvalidAiLimitException.class, () -> service.update("a@x.id", request));

        AiLimitsUpdateRequestDTO huge = requestFrom(service.get());
        huge.getTiers().get("FREE").getTestcase().setMaxOutputTokens(10_000_000);
        assertThrows(InvalidAiLimitException.class, () -> service.update("a@x.id", huge));
        assertTrue(table.isEmpty());
    }

    @Test
    void unlimitedZeroNeedsExplicitConfirmation() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getTiers().get("FREE").getAutomation().setDailyLimit(0);

        InvalidAiLimitException e = assertThrows(InvalidAiLimitException.class, () -> service.update("a@x.id", request));
        assertTrue(e.getMessage().contains("TANPA BATAS"));
        assertTrue(table.isEmpty());
        verify(activityLog, never()).log(anyString(), anyString());

        request.setConfirmUnlimited(true);
        service.update("a@x.id", request);
        assertEquals(0, table.get("FREE.AUTOMATION_DAILY_LIMIT").getValue());
        assertEquals(0, provider.tier(AiTier.FREE).getDailyLimit());
    }

    @Test
    void zeroIsNotAllowedOnKeysWhereItMeansNothingUseful() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getTiers().get("FREE").getAutomation().setMaxTestCases(0);
        request.setConfirmUnlimited(true);

        assertThrows(InvalidAiLimitException.class, () -> service.update("a@x.id", request));
    }

    @Test
    void missingOrUnknownTiersAndNullValuesAreRejected() {
        AiLimitsUpdateRequestDTO missing = requestFrom(service.get());
        missing.setTiers(new TreeMap<>(missing.getTiers()));
        missing.getTiers().remove("VIP_MONTHLY");
        assertThrows(InvalidAiLimitException.class, () -> service.update("a@x.id", missing));

        AiLimitsUpdateRequestDTO unknown = requestFrom(service.get());
        unknown.setTiers(new TreeMap<>(unknown.getTiers()));
        unknown.getTiers().put("VIP", unknown.getTiers().get("FREE"));
        assertThrows(InvalidAiLimitException.class, () -> service.update("a@x.id", unknown));

        AiLimitsUpdateRequestDTO nulls = requestFrom(service.get());
        nulls.getGlobal().setQuotaCircuitMinutes(null);
        assertThrows(InvalidAiLimitException.class, () -> service.update("a@x.id", nulls));
        assertTrue(table.isEmpty());
    }

    @Test
    void resetOneKeyRestoresTheConfiguredDefault() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getTiers().get("FREE").getAutomation().setDailyLimit(9);
        request.getTiers().get("FREE").getTestcase().setDailyLimit(8);
        service.update("a@x.id", request);
        assertEquals(2, table.size());

        AiLimitsDTO result = service.reset("a@x.id", List.of("FREE.AUTOMATION_DAILY_LIMIT"));

        assertEquals(List.of("FREE.TESTCASE_DAILY_LIMIT"), List.copyOf(table.keySet()));
        assertEquals(3, result.getTiers().get("FREE").getAutomation().getDailyLimit());
        assertEquals(8, result.getTiers().get("FREE").getTestcase().getDailyLimit());
        assertEquals("DEFAULT", result.getSource().get("FREE.AUTOMATION_DAILY_LIMIT"));
        verify(activityLog).log("a@x.id", "AI_LIMIT_RESET:FREE.AUTOMATION_DAILY_LIMIT:9->3");
    }

    @Test
    void resetAllAndUnknownKey() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getGlobal().setQuotaCircuitMinutes(30);
        request.getTiers().get("VIP_MONTHLY").getTestcase().setMaxDrafts(4);
        service.update("a@x.id", request);

        service.reset("a@x.id", null);
        assertTrue(table.isEmpty());
        assertEquals(props.getQuotaCircuitMinutes(), provider.quotaCircuitMinutes());

        assertThrows(InvalidAiLimitException.class, () -> service.reset("a@x.id", List.of("FREE.NGAWUR")));
        assertThrows(InvalidAiLimitException.class, () -> service.reset("a@x.id", List.of("GLOBAL.AUTOMATION_DAILY_LIMIT")));
    }

    @Test
    void auditMessagesFitTheActivityColumn() {
        AiLimitsUpdateRequestDTO request = requestFrom(service.get());
        request.getTiers().get("VIP_YEARLY").getAutomation().setMaxOutputTokens(65_536);
        service.update("a@x.id", request);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(activityLog).log(eq("a@x.id"), msg.capture());
        assertTrue(msg.getValue().length() < 255);
    }
}
