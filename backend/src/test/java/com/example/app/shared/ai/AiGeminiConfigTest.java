// filepath: /backend/src/test/java/com/example/app/shared/ai/AiGeminiConfigTest.java
package com.example.app.shared.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.app.modules.automation.AutomationMessages;
import com.example.app.modules.automation.AutomationTestSupport;
import com.example.app.modules.automation.AutomationTestSupport.MutableClock;
import com.example.app.shared.ai.gemini.GeminiAiClient;
import com.example.app.shared.config.ClockConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Konfigurasi AI untuk Gemini: penjaga produksi, tier akun, anggaran global lintas fitur, dan binding properti Spring. */
class AiGeminiConfigTest {

    private static AiProperties gemini(String billingTier) {
        AiProperties p = new AiProperties();
        p.setProvider("gemini");
        p.setApiKey("AQ.key-uji-bukan-asli");
        p.setBillingTier(billingTier);
        p.getFree().setModel("model-free");
        p.getVip().setModel("model-vip");
        return p;
    }

    private static void validate(AiProperties p, boolean prod) {
        AiConfigurationValidator.validate(p, Set.of("gemini", "fake"), prod);
    }

    // ================= Penjaga produksi =================

    @Test
    void productionRefusesGeminiUnlessTheAccountIsDeclaredPaid() {
        IllegalStateException free = assertThrows(IllegalStateException.class, () -> validate(gemini("free"), true));
        assertTrue(free.getMessage().contains("billing-tier=paid"), free.getMessage());
        assertTrue(free.getMessage().contains("berbayar"));

        assertThrows(IllegalStateException.class, () -> validate(gemini(""), true));
        assertThrows(IllegalStateException.class, () -> validate(gemini("gratis"), true));
        validate(gemini("paid"), true);        // tidak melempar
        validate(gemini("PAID"), true);        // tidak peka huruf besar/kecil
    }

    @Test
    void developmentMayUseTheFreeTierAndTheBillingTierValueMustBeKnown() {
        validate(gemini("free"), false);
        validate(gemini("paid"), false);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validate(gemini("murah"), false));
        assertTrue(e.getMessage().contains("billing-tier"));
    }

    @Test
    void geminiNeedsAKeyButNotABaseUrlAndTheKeyShapeIsNotChecked() {
        AiProperties noKey = gemini("paid");
        noKey.setApiKey("");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validate(noKey, true));
        assertTrue(e.getMessage().contains("app.ai.api-key"));
        assertFalse(e.getMessage().contains("app.ai.base-url"), "Gemini punya alamat bawaan");

        for (String key : List.of("AQ.baru", "AIzaSyLama", "apa-saja")) {
            AiProperties p = gemini("paid");
            p.setApiKey(key);
            validate(p, true);
        }

        AiProperties other = gemini("paid");
        other.setProvider("acme");
        assertTrue(assertThrows(IllegalStateException.class,
                () -> AiConfigurationValidator.validate(other, Set.of("acme"), false)).getMessage().contains("app.ai.base-url"),
                "provider lain tetap wajib menyebut base-url");
    }

    @Test
    void theGeminiSchemaModeAndTheNewLimitsAreValidated() {
        AiProperties badMode = gemini("paid");
        badMode.getGemini().setResponseSchema("ngawur");
        assertTrue(assertThrows(IllegalStateException.class, () -> validate(badMode, false)).getMessage().contains("response-schema"));

        for (String mode : List.of("off", "response-schema", "response-json-schema")) {
            AiProperties ok = gemini("paid");
            ok.getGemini().setResponseSchema(mode);
            validate(ok, false);
        }

        AiProperties badLimits = gemini("paid");
        badLimits.getFree().setTestcaseMaxDrafts(0);
        badLimits.getVip().setTestcaseMaxOutputTokens(0);
        badLimits.getVip().setTestcaseDailyLimit(-1);
        badLimits.getTestcase().setMaxRequirementChars(0);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validate(badLimits, false));
        assertTrue(e.getMessage().contains("app.ai.free.testcase-max-drafts"));
        assertTrue(e.getMessage().contains("app.ai.vip.testcase-max-output-tokens"));
        assertTrue(e.getMessage().contains("app.ai.vip.testcase-daily-limit"));
        assertTrue(e.getMessage().contains("app.ai.testcase.max-requirement-chars"));
    }

    @Test
    void fakeAndEmptyProvidersAreUnaffectedByTheBillingRule() {
        AiProperties fake = new AiProperties();
        fake.setProvider("fake");
        fake.setBillingTier("free");
        AiConfigurationValidator.validate(fake, Set.of("fake"), false);    // dev: boleh
        AiConfigurationValidator.validate(new AiProperties(), Set.of(), true); // provider kosong di prod: boleh
        assertTrue(assertThrows(IllegalStateException.class,
                () -> AiConfigurationValidator.validate(fake, Set.of("fake"), true)).getMessage().contains("fake"));
    }

    // ================= Tier akun & bawaan =================

    @Test
    void sandboxMeansARealProviderOnAFreeTierAccount() {
        assertTrue(gemini("free").isSandbox());
        assertFalse(gemini("paid").isSandbox());
        assertFalse(gemini(" Paid ").isSandbox());
        AiProperties fake = gemini("free");
        fake.setProvider("fake");
        assertFalse(fake.isSandbox(), "provider fake tidak mengirim data ke mana pun");
        assertFalse(new AiProperties().isSandbox(), "AI nonaktif tidak punya sandbox");
    }

    @Test
    void theDefaultsMatchTheAgreedLimits() {
        AiProperties p = new AiProperties();

        assertEquals("free", p.getBillingTier());
        assertEquals(3, p.getFree().getTestcaseDailyLimit());
        assertEquals(3, p.getFree().getTestcaseMaxDrafts());
        assertEquals(4096, p.getFree().getTestcaseMaxOutputTokens());
        assertEquals(90, p.getFree().getTestcaseTimeoutSeconds());
        assertEquals(15, p.getVip().getTestcaseDailyLimit());     // BUKAN 30 (itu kuota Automation)
        assertEquals(15, p.getVip().getTestcaseMaxDrafts());
        assertEquals(8192, p.getVip().getTestcaseMaxOutputTokens());
        assertEquals(120, p.getVip().getTestcaseTimeoutSeconds());
        assertEquals(30, p.getVip().getDailyLimit(), "kuota Automation VIP tidak berubah");
        assertEquals(3, p.getFree().getDailyLimit());
        assertEquals(6000, p.getTestcase().getMaxRequirementChars());
        assertEquals(7, p.getTestcase().getDraftRetentionDays());
        assertEquals("off", p.getGemini().getResponseSchema());
        assertNull(p.getGemini().getThinkingBudget());
        assertNull(p.getGemini().getTemperature());
    }

    @Test
    void theGenericMessagesAreIdenticalToAutomationsSoUsersSeeOneVoice() {
        assertEquals(AutomationMessages.AI_UNAVAILABLE_MESSAGE, AiMessages.AI_UNAVAILABLE_MESSAGE);
        assertEquals(AutomationMessages.AI_UNAVAILABLE, AiMessages.AI_UNAVAILABLE);
        assertEquals(AutomationMessages.AI_INVALID_OUTPUT_MESSAGE, AiMessages.AI_INVALID_OUTPUT_MESSAGE);
        assertEquals(AutomationMessages.GENERATION_FAILED_MESSAGE, AiMessages.GENERATION_FAILED_MESSAGE);
        assertEquals(AutomationMessages.GENERATION_INTERRUPTED_MESSAGE, AiMessages.GENERATION_INTERRUPTED_MESSAGE);
        assertEquals(AutomationMessages.SERVER_BUSY_MESSAGE, AiMessages.SERVER_BUSY_MESSAGE);
        assertEquals(AutomationMessages.PLAN_LIMIT, AiMessages.PLAN_LIMIT);
        assertEquals(AutomationMessages.IN_PROGRESS, AiMessages.IN_PROGRESS);
    }

    // ================= Anggaran global lintas fitur =================

    @Test
    void theGlobalBudgetSumsEveryFeatureAndZeroMeansUnlimited() {
        MutableClock clock = new MutableClock("2026-10-05T10:00:00Z");
        AiProperties p = new AiProperties();
        long[] featureA = {3};
        long[] featureB = {2};
        AiUsageCounter a = since -> featureA[0];
        AiUsageCounter b = since -> featureB[0];
        AiGlobalBudget budget = new AiGlobalBudget(p, List.of(a, b), clock);

        p.setGlobalDailyLimit(0);
        assertFalse(budget.isExceeded());                 // 0 = tanpa batas
        assertEquals(5, budget.usedToday());

        p.setGlobalDailyLimit(6);
        assertFalse(budget.isExceeded());                 // 5 < 6
        featureB[0] = 3;
        assertTrue(budget.isExceeded());                  // 3 + 3 = 6 >= 6: hitungan LINTAS fitur
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), budget.startOfToday());
    }

    @Test
    void exceedingTheGlobalBudgetLogsAnAlertForTheAdmin() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AiGlobalBudget.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AiProperties p = new AiProperties();
            p.setGlobalDailyLimit(1);
            new AiGlobalBudget(p, List.of(since -> 5L), new MutableClock("2026-10-05T10:00:00Z")).isExceeded();

            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("[AI-ALERT]")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ================= Binding properti & pemasangan Spring =================

    @Configuration
    @EnableConfigurationProperties
    static class Wiring {
        @Bean
        ObjectMapper objectMapper() {
            return AutomationTestSupport.mapper();
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties, String... profiles) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        if (profiles.length > 0) {
            ctx.getEnvironment().setActiveProfiles(profiles);
        }
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", new HashMap<>(properties)));
        ctx.register(Wiring.class, ClockConfig.class, AiProperties.class, AiGateway.class, AiGlobalBudget.class,
                AiConfigurationValidator.class, GeminiAiClient.class);
        return ctx;
    }

    private static Map<String, Object> geminiProperties(String billingTier) {
        Map<String, Object> p = new HashMap<>();
        p.put("app.ai.provider", "gemini");
        p.put("app.ai.api-key", "AQ.key-uji-bukan-asli");
        p.put("app.ai.billing-tier", billingTier);
        p.put("app.ai.free.model", "model-free");
        p.put("app.ai.vip.model", "model-vip");
        return p;
    }

    @Test
    void springBindsTheKebabCasePropertiesIncludingEmptyOptionalNumbersAndCreatesTheGeminiClient() {
        Map<String, Object> properties = geminiProperties("paid");
        properties.put("app.ai.gemini.thinking-budget", "");                 // persis seperti default kosong di application-prod.properties
        properties.put("app.ai.gemini.temperature", "");
        properties.put("app.ai.gemini.response-schema", "off");
        properties.put("app.ai.vip.testcase-daily-limit", "20");
        properties.put("app.ai.free.testcase-max-drafts", "2");
        properties.put("app.ai.testcase.max-requirement-chars", "1234");
        properties.put("app.ai.testcase.draft-retention-days", "3");

        try (AnnotationConfigApplicationContext ctx = context(properties, "dev")) {
            ctx.refresh();
            AiProperties bound = ctx.getBean(AiProperties.class);

            assertTrue(ctx.getBean(AiGateway.class).isEnabled());
            assertEquals(1, ctx.getBeansOfType(GeminiAiClient.class).size());
            assertNull(bound.getGemini().getThinkingBudget(), "string kosong -> null (tidak dikirim ke Gemini)");
            assertNull(bound.getGemini().getTemperature());
            assertEquals(20, bound.getVip().getTestcaseDailyLimit());
            assertEquals(15, bound.getVip().getTestcaseMaxDrafts());          // tidak disebut -> bawaan VIP
            assertEquals(2, bound.getFree().getTestcaseMaxDrafts());
            assertEquals(1234, bound.getTestcase().getMaxRequirementChars());
            assertEquals(3, bound.getTestcase().getDraftRetentionDays());
            assertEquals("paid", bound.getBillingTier());
        }
    }

    @Test
    void springBindsNumericOptionalSettingsWhenTheyAreFilledIn() {
        Map<String, Object> properties = geminiProperties("free");
        properties.put("app.ai.gemini.thinking-budget", "0");
        properties.put("app.ai.gemini.temperature", "0.25");

        try (AnnotationConfigApplicationContext ctx = context(properties, "dev")) {
            ctx.refresh();
            AiProperties bound = ctx.getBean(AiProperties.class);

            assertEquals(Integer.valueOf(0), bound.getGemini().getThinkingBudget());
            assertEquals(Double.valueOf(0.25), bound.getGemini().getTemperature());
            assertTrue(bound.isSandbox());
        }
    }

    @Test
    void theApplicationStartsInProdWithPaidAndRefusesToStartWithTheFreeTier() {
        try (AnnotationConfigApplicationContext ok = context(geminiProperties("paid"), "prod")) {
            ok.refresh();
            assertTrue(ok.getBean(AiGateway.class).isEnabled());
        }

        try (AnnotationConfigApplicationContext refused = context(geminiProperties("free"), "prod")) {
            BeanCreationException e = assertThrows(BeanCreationException.class, refused::refresh);
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertTrue(root.getMessage().contains("billing-tier=paid"), root.getMessage());
        }
    }
}
