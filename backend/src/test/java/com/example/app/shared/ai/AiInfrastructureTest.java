// filepath: /backend/src/test/java/com/example/app/shared/ai/AiInfrastructureTest.java
package com.example.app.shared.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.app.modules.automation.AutomationTestSupport;
import com.example.app.modules.automation.AutomationTestSupport.MutableClock;
import com.example.app.modules.automation.ai.AutomationPromptBuilder;
import com.example.app.modules.automation.ai.FakeAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInfrastructureTest {

    // ---------- pendukung ----------

    static final class StubClient implements AiClient {
        int calls;
        AiRequest lastRequest;
        Supplier<AiResponse> behavior = () -> new AiResponse("ok", 1, 1, "stub-model");

        @Override
        public String providerId() {
            return "stub";
        }

        @Override
        public AiResponse generate(AiRequest request) {
            calls++;
            lastRequest = request;
            return behavior.get();
        }
    }

    static final class LogCapture implements AutoCloseable {
        private final ch.qos.logback.classic.Logger logger;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        LogCapture(Class<?> type) {
            logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type);
            appender.start();
            logger.addAppender(appender);
        }

        List<ILoggingEvent> errors() {
            return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        }

        long alertCount() {
            return errors().stream().filter(e -> e.getFormattedMessage().contains("[AI-ALERT]")).count();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
        }
    }

    private AiProperties props(String provider) {
        AiProperties p = new AiProperties();
        p.setProvider(provider);
        p.setApiKey("sk-SANGAT-RAHASIA-123");
        p.getFree().setModel("model-free");
        p.getFree().setMaxOutputTokens(1000);
        p.getFree().setTimeoutSeconds(30);
        p.getVip().setModel("model-vip");
        p.getVip().setMaxOutputTokens(9000);
        p.getVip().setTimeoutSeconds(90);
        return p;
    }

    // ---------- AiTier ----------

    @Test
    void userTypesMapToTiers() {
        assertEquals(AiTier.FREE, AiTier.fromUserType("FREE"));
        assertEquals(AiTier.VIP, AiTier.fromUserType("VIP_MONTHLY"));
        assertEquals(AiTier.VIP, AiTier.fromUserType("VIP_YEARLY"));
        assertEquals(AiTier.FREE, AiTier.fromUserType("SESUATU_YANG_BARU")); // tidak dikenal -> FREE (aman utk biaya)
        assertEquals(AiTier.FREE, AiTier.fromUserType(null));
    }

    // ---------- AiGateway ----------

    @Test
    void gatewayIsDisabledWithoutProviderAndRefusesToGenerate() {
        AiGateway gateway = new AiGateway(props(""), List.of(new StubClient()), Clock.systemUTC());

        assertFalse(gateway.isEnabled());
        assertFalse(gateway.canAcceptRequests());
        AiProviderException e = assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.FREE, "s", "u"));
        assertEquals(AiProviderException.Kind.UNAVAILABLE, e.getKind());
    }

    @Test
    void gatewayPassesTheTierSpecificModelLimitsAndTimeoutToTheClient() {
        StubClient client = new StubClient();
        AiGateway gateway = new AiGateway(props("stub"), List.of(client), Clock.systemUTC());

        gateway.generate(AiTier.FREE, "sys", "usr");
        assertEquals("model-free", client.lastRequest.model());
        assertEquals(1000, client.lastRequest.maxOutputTokens());
        assertEquals(30, client.lastRequest.timeout().toSeconds());

        gateway.generate(AiTier.VIP, "sys", "usr");
        assertEquals("model-vip", client.lastRequest.model());
        assertEquals(9000, client.lastRequest.maxOutputTokens());
        assertEquals(90, client.lastRequest.timeout().toSeconds());
        assertEquals("sys", client.lastRequest.systemPrompt());
    }

    @Test
    void quotaExhaustionOpensTheCircuitAlertsOncePerWindowAndStopsCallingTheProvider() {
        MutableClock clock = new MutableClock("2026-10-05T10:00:00Z");
        StubClient client = new StubClient();
        client.behavior = () -> {
            throw new AiProviderException(AiProviderException.Kind.QUOTA_EXHAUSTED, "insufficient_quota (detail provider)");
        };
        AiGateway gateway = new AiGateway(props("stub"), List.of(client), clock);

        try (LogCapture log = new LogCapture(AiGateway.class)) {
            assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.FREE, "s", "u"));
            assertEquals(1, client.calls);
            assertFalse(gateway.canAcceptRequests());      // circuit terbuka
            assertTrue(gateway.isEnabled());                // tetap "aktif" menurut konfigurasi

            // beberapa panggilan lagi di dalam jendela: DITAHAN, provider tidak dipanggil, tidak ada alert baru
            for (int i = 0; i < 3; i++) {
                AiProviderException held = assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.VIP, "s", "u"));
                assertEquals(AiProviderException.Kind.QUOTA_EXHAUSTED, held.getKind());
            }
            assertEquals(1, client.calls);
            assertEquals(1, log.alertCount());
            assertTrue(log.errors().get(0).getFormattedMessage().contains("HABIS"));

            // setelah jendela (10 menit) lewat: provider dicoba lagi; masih habis -> alert baru
            clock.advanceHours(1);
            assertTrue(gateway.canAcceptRequests());
            assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.FREE, "s", "u"));
            assertEquals(2, client.calls);
            assertEquals(2, log.alertCount());
        }
    }

    @Test
    void transientFailuresDoNotOpenTheCircuit() {
        StubClient client = new StubClient();
        client.behavior = () -> {
            throw new AiProviderException(AiProviderException.Kind.RATE_LIMITED, "429 sementara");
        };
        AiGateway gateway = new AiGateway(props("stub"), List.of(client), Clock.systemUTC());

        assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.FREE, "s", "u"));
        assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.FREE, "s", "u"));

        assertEquals(2, client.calls);
        assertTrue(gateway.canAcceptRequests());
    }

    @Test
    void rawExceptionsFromABadAdapterAreWrappedNotLeaked() {
        StubClient client = new StubClient();
        client.behavior = () -> {
            throw new IllegalStateException("401 Unauthorized: Bearer sk-SANGAT-RAHASIA-123");
        };
        AiGateway gateway = new AiGateway(props("stub"), List.of(client), Clock.systemUTC());

        AiProviderException e = assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.FREE, "s", "u"));

        assertEquals(AiProviderException.Kind.UNAVAILABLE, e.getKind());
        assertFalse(e.getMessage().contains("sk-SANGAT-RAHASIA-123"), "pesan pembungkus tidak boleh memuat isi exception mentah");
    }

    @Test
    void apiKeyNeverAppearsInGatewayLogs() {
        StubClient client = new StubClient();
        client.behavior = () -> {
            throw new AiProviderException(AiProviderException.Kind.QUOTA_EXHAUSTED, "kuota habis");
        };
        AiGateway gateway = new AiGateway(props("stub"), List.of(client), Clock.systemUTC());

        try (LogCapture log = new LogCapture(AiGateway.class)) {
            assertThrows(AiProviderException.class, () -> gateway.generate(AiTier.FREE, "s", "u"));
            assertTrue(log.errors().size() >= 1);
            for (ILoggingEvent event : log.appender.list) {
                assertFalse(event.getFormattedMessage().contains("sk-SANGAT-RAHASIA-123"));
            }
        }
    }

    // ---------- AiConfigurationValidator (murni) ----------

    @Test
    void emptyProviderIsAlwaysAcceptedEvenInProduction() {
        AiConfigurationValidator.validate(props(""), Set.of(), true);
        AiConfigurationValidator.validate(props("   "), Set.of(), true);
    }

    @Test
    void fakeProviderIsForbiddenInProductionButFineElsewhere() {
        AiConfigurationValidator.validate(props("fake"), Set.of("fake"), false);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AiConfigurationValidator.validate(props("fake"), Set.of("fake"), true));
        assertTrue(e.getMessage().contains("prod"));
        assertTrue(e.getMessage().contains("kode palsu"));
    }

    @Test
    void providerWithoutAdapterFailsStartWithAHelpfulMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AiConfigurationValidator.validate(props("acme"), Set.of("fake"), false));
        assertTrue(e.getMessage().contains("acme") && e.getMessage().contains("adapter"));
    }

    @Test
    void realProviderNeedsBaseUrlApiKeyAndAValidModelAndLimitsPerTier() {
        AiProperties incomplete = props("acme");
        incomplete.setApiKey("");
        incomplete.getVip().setModel("");
        incomplete.getFree().setMaxTestCases(0);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AiConfigurationValidator.validate(incomplete, Set.of("acme"), true));
        assertTrue(e.getMessage().contains("app.ai.base-url"));
        assertTrue(e.getMessage().contains("app.ai.api-key"));
        assertTrue(e.getMessage().contains("app.ai.vip.model"));
        assertTrue(e.getMessage().contains("app.ai.free.max-test-cases"));

        AiProperties complete = props("acme");
        complete.setBaseUrl("https://ai.example.invalid");
        AiConfigurationValidator.validate(complete, Set.of("acme"), true); // tidak melempar
    }

    // ---------- Pemasangan di konteks Spring sungguhan ----------

    @Configuration
    @EnableConfigurationProperties
    static class WiringConfig {
        @Bean
        ObjectMapper objectMapper() {
            return AutomationTestSupport.mapper();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        AutomationPromptBuilder promptBuilder(ObjectMapper mapper) {
            return new AutomationPromptBuilder(mapper);
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties, String... profiles) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        if (profiles.length > 0) {
            ctx.getEnvironment().setActiveProfiles(profiles);
        }
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", new HashMap<>(properties)));
        ctx.register(WiringConfig.class, AiProperties.class, AiGateway.class, AiConfigurationValidator.class, FakeAiClient.class);
        return ctx;
    }

    @Test
    void contextStartsNormallyWithNoProviderAndTheFakeClientIsNotEvenCreated() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of())) {
            ctx.refresh();

            assertFalse(ctx.getBean(AiGateway.class).isEnabled());
            assertTrue(ctx.getBeansOfType(AiClient.class).isEmpty());
        }
    }

    @Test
    void contextBindsKebabCaseTierPropertiesAndEnablesTheFakeProviderOutsideProd() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of(
                "app.ai.provider", "fake",
                "app.ai.fake.delay-millis", "0",
                "app.ai.free.max-test-cases", "7",
                "app.ai.vip.daily-limit", "99",
                "app.ai.global-daily-limit", "500"), "dev")) {
            ctx.refresh();
            AiGateway gateway = ctx.getBean(AiGateway.class);

            assertTrue(gateway.isEnabled());
            assertEquals(7, gateway.tier(AiTier.FREE).getMaxTestCases());
            assertEquals(99, gateway.tier(AiTier.VIP).getDailyLimit());
            assertEquals(500, ctx.getBean(AiProperties.class).getGlobalDailyLimit());
            assertEquals(1, ctx.getBeansOfType(AiClient.class).size());
        }
    }

    @Test
    void applicationRefusesToStartWithTheFakeProviderInTheProdProfile() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of("app.ai.provider", "fake"), "prod")) {
            BeanCreationException e = assertThrows(BeanCreationException.class, ctx::refresh);

            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertTrue(root.getMessage().contains("prod"), root.getMessage());
        }
    }

    @Test
    void applicationRefusesToStartWithAProviderThatHasNoAdapter() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of("app.ai.provider", "acme", "app.ai.api-key", "k"))) {
            BeanCreationException e = assertThrows(BeanCreationException.class, ctx::refresh);

            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertTrue(root.getMessage().contains("adapter"), root.getMessage());
        }
    }
}
