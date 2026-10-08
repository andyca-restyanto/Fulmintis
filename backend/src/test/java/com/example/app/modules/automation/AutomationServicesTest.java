// filepath: /backend/src/test/java/com/example/app/modules/automation/AutomationServicesTest.java
package com.example.app.modules.automation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.automation.AutomationTestSupport.GenerationStore;
import com.example.app.modules.automation.AutomationTestSupport.MutableClock;
import com.example.app.modules.automation.AutomationTestSupport.SetupStore;
import com.example.app.modules.automation.ai.AutomationOutputParser;
import com.example.app.modules.automation.ai.AutomationPromptBuilder;
import com.example.app.modules.automation.ai.FakeAiClient;
import com.example.app.modules.automation.dto.AutomationGenerationCreatedDTO;
import com.example.app.modules.automation.dto.AutomationGenerationResponseDTO;
import com.example.app.modules.automation.dto.AutomationSetupRequestDTO;
import com.example.app.modules.automation.dto.AutomationSetupResponseDTO;
import com.example.app.modules.automation.dto.GenerateAutomationRequestDTO;
import com.example.app.modules.automation.entity.AutomationGeneration;
import com.example.app.modules.automation.exception.AutomationApiException;
import com.example.app.modules.automation.service.AutomationGenerationService.AutomationDownload;
import com.example.app.modules.automation.service.AutomationGenerationWorker;
import com.example.app.modules.automation.service.AutomationQuotaService;
import com.example.app.modules.automation.service.AutomationStartupRecovery;
import com.example.app.modules.automation.service.AutomationUsageCounter;
import com.example.app.modules.automation.service.impl.AutomationGenerationServiceImpl;
import com.example.app.modules.automation.service.impl.AutomationSetupServiceImpl;
import com.example.app.modules.automation.service.impl.ExecutorAutomationJobDispatcher;
import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.exception.ProjectAccessForbiddenException;
import com.example.app.modules.project.exception.ProjectNotFoundException;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.shared.ai.AiGateway;
import com.example.app.shared.ai.AiGlobalBudget;
import com.example.app.shared.ai.AiProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur lengkap setup -> generate -> hasil -> unduh dgn klien AI palsu; tanpa DB, tanpa Spring, tanpa API key. */
class AutomationServicesTest {

    private static final String EMAIL = "andi@example.com";

    /** Rangkaian lengkap objek yang saling terhubung seperti di aplikasi sungguhan. */
    private static final class Env {
        final MutableClock clock = new MutableClock("2026-10-05T10:00:00Z");
        final Project project = Project.builder().id(UUID.randomUUID()).build();
        final Project otherProject = Project.builder().id(UUID.randomUUID()).build();
        final AiProperties props = new AiProperties();
        final SetupStore setups = new SetupStore();
        final GenerationStore generations = new GenerationStore(clock);
        final Map<UUID, TestCase> testCases = new LinkedHashMap<>();
        final List<UUID> dispatched = new ArrayList<>();
        final List<String> activity = new ArrayList<>();
        String userType = "FREE";
        boolean owner = true;

        final AiGateway gateway;
        final AutomationQuotaService quota;
        final AutomationGenerationWorker worker;
        final AutomationGenerationServiceImpl service;
        final AutomationSetupServiceImpl setupService;

        Env(String fakeMode, boolean aiEnabled) {
            var mapper = AutomationTestSupport.mapper();
            var promptBuilder = new AutomationPromptBuilder(mapper);
            props.setProvider(aiEnabled ? "fake" : "");
            gateway = new AiGateway(props,
                    aiEnabled ? List.of(new FakeAiClient(mapper, promptBuilder, fakeMode, 0)) : List.of(), clock);
            quota = new AutomationQuotaService(generations.repository(), props,
                    new AiGlobalBudget(props, List.of(new AutomationUsageCounter(generations.repository())), clock), clock);

            TestCaseRepository testCaseRepository = (TestCaseRepository) Proxy.newProxyInstance(
                    TestCaseRepository.class.getClassLoader(), new Class<?>[]{TestCaseRepository.class},
                    (p, m, a) -> {
                        if (m.getName().equals("findAllById")) {
                            List<TestCase> found = new ArrayList<>();
                            for (Object id : (Iterable<?>) a[0]) {
                                if (testCases.containsKey(id)) found.add(testCases.get(id));
                            }
                            return found;
                        }
                        throw new UnsupportedOperationException(m.getName());
                    });
            UserRepository userRepository = (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                    (p, m, a) -> {
                        if (m.getName().equals("findByEmail")) {
                            return Optional.of(User.builder().email((String) a[0]).userType(userType).build());
                        }
                        throw new UnsupportedOperationException(m.getName());
                    });
            ProjectAccessService access = new ProjectAccessService(null, null) {
                @Override
                public ProjectCollaboration requireMember(String userEmail, UUID projectId) {
                    if (!project.getId().equals(projectId)) {
                        throw new ProjectNotFoundException();
                    }
                    return ProjectCollaboration.builder().project(project).projectTeam(owner ? "OWNER" : "COLLABORATOR").build();
                }

                @Override
                public ProjectCollaboration requireOwner(String userEmail, UUID projectId, String forbiddenMessage) {
                    ProjectCollaboration c = requireMember(userEmail, projectId);
                    if (!"OWNER".equals(c.getProjectTeam())) {
                        throw new ProjectAccessForbiddenException(forbiddenMessage);
                    }
                    return c;
                }
            };

            worker = new AutomationGenerationWorker(generations.repository(), testCaseRepository, gateway, promptBuilder,
                    new AutomationOutputParser(mapper), mapper, clock);
            service = new AutomationGenerationServiceImpl(access, setups.repository(), generations.repository(), testCaseRepository,
                    userRepository, gateway, quota, dispatched::add, (email, act) -> activity.add(email + ":" + act), mapper, clock);
            setupService = new AutomationSetupServiceImpl(access, setups.repository(), (email, act) -> activity.add(email + ":" + act));
        }

        void configure(AutomationFramework f, AutomationLanguage l, AutomationPattern p) {
            AutomationSetupRequestDTO request = new AutomationSetupRequestDTO();
            request.setFramework(f);
            request.setLanguage(l);
            request.setPattern(p);
            request.setStructureNotes("pages/, tests/");
            setupService.saveSetup(EMAIL, project.getId(), request);
        }

        List<UUID> newTestCases(int count) {
            List<UUID> ids = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                TestCase tc = AutomationTestSupport.testCase(project, "Test case " + i, "1. langkah " + i, "1. hasil " + i);
                testCases.put(tc.getId(), tc);
                ids.add(tc.getId());
            }
            return ids;
        }

        AutomationGenerationCreatedDTO submit(List<UUID> ids) {
            GenerateAutomationRequestDTO request = new GenerateAutomationRequestDTO();
            request.setTestCaseIds(ids);
            return service.submit(EMAIL, project.getId(), request);
        }

        /** Menjalankan semua job yang sudah di-dispatch (menggantikan thread latar belakang). */
        void runWorker() {
            List<UUID> pending = new ArrayList<>(dispatched);
            dispatched.clear();
            pending.forEach(worker::run);
        }

        AutomationGenerationResponseDTO get(UUID id) {
            return service.getGeneration(EMAIL, project.getId(), id);
        }
    }

    private static Env readyEnv() {
        Env env = new Env("NONE", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.PAGE_OBJECT_MODEL);
        return env;
    }

    private static AutomationApiException rejected(Runnable action) {
        return assertThrows(AutomationApiException.class, action::run);
    }

    // =================== Setup ===================

    @Test
    void projectWithoutSetupReportsNotConfigured() {
        Env env = new Env("NONE", true);

        AutomationSetupResponseDTO setup = env.setupService.getSetup(EMAIL, env.project.getId());

        assertFalse(setup.isConfigured());
        assertNull(setup.getFramework());
    }

    @Test
    void savingTwiceUpdatesTheSameRowSoThereIsNeverMoreThanOneSetupPerProject() {
        Env env = new Env("NONE", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.PAGE_OBJECT_MODEL);
        UUID firstId = env.setups.byProject.get(env.project.getId()).getId();

        env.configure(AutomationFramework.SELENIUM, AutomationLanguage.JAVA, AutomationPattern.SIMPLE);

        assertEquals(1, env.setups.byProject.size());
        assertEquals(firstId, env.setups.byProject.get(env.project.getId()).getId());
        AutomationSetupResponseDTO now = env.setupService.getSetup(EMAIL, env.project.getId());
        assertEquals(AutomationFramework.SELENIUM, now.getFramework());
        assertEquals(AutomationLanguage.JAVA, now.getLanguage());
        assertEquals(AutomationPattern.SIMPLE, now.getPattern());
        assertTrue(env.activity.contains(EMAIL + ":SAVE_AUTOMATION_SETUP"));
    }

    @Test
    void setupDefaultsToPageObjectModelAndNormalisesNotes() {
        Env env = new Env("NONE", true);
        AutomationSetupRequestDTO request = new AutomationSetupRequestDTO();
        request.setFramework(AutomationFramework.CYPRESS);
        request.setLanguage(AutomationLanguage.TYPESCRIPT);
        request.setStructureNotes("   ");

        AutomationSetupResponseDTO saved = env.setupService.saveSetup(EMAIL, env.project.getId(), request);

        assertEquals(AutomationPattern.PAGE_OBJECT_MODEL, saved.getPattern());
        assertNull(saved.getStructureNotes());
    }

    @Test
    void backendRejectsInvalidFrameworkLanguageCombinationsEvenIfTheUiAllowedThem() {
        Env env = new Env("NONE", true);
        for (AutomationLanguage language : List.of(AutomationLanguage.JAVA, AutomationLanguage.PYTHON)) {
            AutomationSetupRequestDTO request = new AutomationSetupRequestDTO();
            request.setFramework(AutomationFramework.CYPRESS);
            request.setLanguage(language);

            AutomationApiException e = rejected(() -> env.setupService.saveSetup(EMAIL, env.project.getId(), request));

            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
            assertEquals("UNSUPPORTED_COMBINATION", e.getErrorCode());
            assertEquals("Cypress tidak mendukung " + language.displayName() + ".", e.getMessage());
        }
        assertEquals(0, env.setups.byProject.size()); // tidak ada yang tersimpan
    }

    @Test
    void onlyTheOwnerCanChangeOrDeleteTheSetupButEveryMemberCanReadIt() {
        Env env = readyEnv();
        env.owner = false;

        assertTrue(env.setupService.getSetup(EMAIL, env.project.getId()).isConfigured());
        assertNotNull(env.setupService.getOptions(EMAIL, env.project.getId()));
        AutomationSetupRequestDTO request = new AutomationSetupRequestDTO();
        request.setFramework(AutomationFramework.PLAYWRIGHT);
        request.setLanguage(AutomationLanguage.PYTHON);
        assertThrows(ProjectAccessForbiddenException.class, () -> env.setupService.saveSetup(EMAIL, env.project.getId(), request));
        assertThrows(ProjectAccessForbiddenException.class, () -> env.setupService.deleteSetup(EMAIL, env.project.getId()));
        assertEquals(AutomationLanguage.TYPESCRIPT, env.setups.byProject.get(env.project.getId()).getLanguage()); // tidak berubah
    }

    @Test
    void concurrentFirstSaveConflictAnswersClearlyAndDeleteIsIdempotent() {
        Env env = new Env("NONE", true);
        env.setups.failFlushWithConflict = true;
        AutomationSetupRequestDTO request = new AutomationSetupRequestDTO();
        request.setFramework(AutomationFramework.PLAYWRIGHT);
        request.setLanguage(AutomationLanguage.JAVA);

        AutomationApiException e = rejected(() -> env.setupService.saveSetup(EMAIL, env.project.getId(), request));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("SETUP_CONFLICT", e.getErrorCode());

        env.setupService.deleteSetup(EMAIL, env.project.getId()); // tidak ada setup: tidak error
        env.setups.failFlushWithConflict = false;
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.JAVA, AutomationPattern.SIMPLE);
        env.setupService.deleteSetup(EMAIL, env.project.getId());
        assertFalse(env.setupService.getSetup(EMAIL, env.project.getId()).isConfigured());
    }

    // =================== Generate: alur normal ===================

    @Test
    void happyPathQueuesThenSucceedsWithFilesPerTestCaseAndDownloadableZip() throws Exception {
        Env env = readyEnv();
        List<UUID> ids = env.newTestCases(2);

        AutomationGenerationCreatedDTO created = env.submit(ids);
        assertEquals(AutomationGenerationStatus.QUEUED, created.getStatus());
        assertEquals(List.of(created.getId()), env.dispatched);   // sudah di-dispatch ke latar belakang
        assertEquals(AutomationGenerationStatus.QUEUED, env.get(created.getId()).getStatus());
        assertTrue(env.get(created.getId()).getFiles().isEmpty());

        env.runWorker();

        AutomationGenerationResponseDTO done = env.get(created.getId());
        assertEquals(AutomationGenerationStatus.SUCCEEDED, done.getStatus());
        assertEquals(3, done.getFiles().size());                  // 2 test + 1 page object (pola POM)
        assertTrue(done.getFiles().stream().anyMatch(f -> f.path().equals("tests/test-case-1.spec.ts")));
        assertTrue(done.getFiles().stream().anyMatch(f -> f.path().equals("pages/BasePage.ts")));
        assertNull(done.getErrorCode());
        assertNotNull(done.getFinishedAt());
        assertTrue(env.activity.contains(EMAIL + ":GENERATE_AUTOMATION"));

        AutomationGeneration row = env.generations.rows.get(created.getId());
        assertEquals("FREE", row.getTier());
        assertEquals("automation-v1", row.getPromptVersion());
        assertTrue(row.getInputTokens() > 0 && row.getOutputTokens() > 0);
        assertEquals(2, row.getTestCaseCount());

        AutomationDownload download = env.service.download(EMAIL, env.project.getId(), created.getId());
        assertEquals("automation-playwright-typescript-20261005.zip", download.fileName());
        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(download.content()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) entries.add(entry.getName());
        }
        assertEquals(4, entries.size());
        assertEquals("README-AI-GENERATED.txt", entries.get(0));
    }

    @Test
    void everyValidFrameworkLanguageCombinationWorksFromSetupToDownloadableZip() {
        int combinations = 0;
        for (AutomationFramework framework : AutomationFramework.values()) {
            for (AutomationLanguage language : AutomationLanguage.values()) {
                if (!AutomationCompatibility.isSupported(framework, language)) {
                    continue;
                }
                combinations++;
                Env env = new Env("NONE", true);
                env.props.getFree().setDailyLimit(0);
                env.configure(framework, language, AutomationPattern.PAGE_OBJECT_MODEL);

                AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(2));
                env.runWorker();

                AutomationGenerationResponseDTO done = env.get(created.getId());
                String label = framework + "/" + language;
                assertEquals(AutomationGenerationStatus.SUCCEEDED, done.getStatus(), label);
                assertEquals(3, done.getFiles().size(), label);                   // 2 test + 1 page object
                assertEquals(framework, done.getFramework(), label);
                assertEquals(language, done.getLanguage(), label);
                AutomationDownload zip = env.service.download(EMAIL, env.project.getId(), created.getId());
                assertTrue(zip.content().length > 100, label);
                assertTrue(zip.fileName().startsWith("automation-" + framework.name().toLowerCase()), label);
            }
        }
        assertEquals(10, combinations);
    }

    @Test
    void theRunKeepsASnapshotOfTheSetupEvenIfItChangesAfterwards() {
        Env env = readyEnv();
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));

        env.configure(AutomationFramework.CYPRESS, AutomationLanguage.JAVASCRIPT, AutomationPattern.SIMPLE); // diubah SETELAH submit
        env.runWorker();

        AutomationGenerationResponseDTO done = env.get(created.getId());
        assertEquals(AutomationFramework.PLAYWRIGHT, done.getFramework());
        assertEquals(AutomationLanguage.TYPESCRIPT, done.getLanguage());
        assertTrue(done.getFiles().stream().allMatch(f -> f.path().endsWith(".ts")));
    }

    @Test
    void wrappedJsonFromTheModelIsStillAccepted() {
        Env env = new Env("WRAPPED_OUTPUT", true);
        env.configure(AutomationFramework.CYPRESS, AutomationLanguage.JAVASCRIPT, AutomationPattern.SIMPLE);
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(2));

        env.runWorker();

        assertEquals(AutomationGenerationStatus.SUCCEEDED, env.get(created.getId()).getStatus());
        assertEquals(2, env.get(created.getId()).getFiles().size());
    }

    // =================== Generate: penolakan ===================

    @Test
    void extraCompanionFilesFromTheAiAreSkippedAndReportedInsteadOfFailingTheJob() {
        // Kasus nyata dari Gemini: JSON benar, tetapi ada .gitignore dan Dockerfile di samping kode.
        Env env = new Env("EXTRA_FILES", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.PYTHON, AutomationPattern.PAGE_OBJECT_MODEL);
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));

        env.runWorker();

        AutomationGenerationResponseDTO done = env.get(created.getId());
        assertEquals(AutomationGenerationStatus.SUCCEEDED, done.getStatus());
        assertNull(done.getErrorCode());
        assertTrue(done.getFiles().stream().noneMatch(f -> f.path().equals(".gitignore") || f.path().equals("Dockerfile")));
        assertTrue(done.getFiles().stream().anyMatch(f -> f.path().endsWith(".py")), "kode yang berguna tetap ada");
        assertTrue(done.getNotes().contains(".gitignore") && done.getNotes().contains("Dockerfile"), done.getNotes());
        assertTrue(done.getNotes().contains("FAKE AI OUTPUT"), "catatan AI tetap ada");
    }

    @Test
    void generatingWithoutASetupIsRejectedWithAClearConflict() {
        Env env = new Env("NONE", true);

        AutomationApiException e = rejected(() -> env.submit(env.newTestCases(1)));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("AUTOMATION_SETUP_REQUIRED", e.getErrorCode());
        assertEquals(0, env.generations.rows.size());
    }

    @Test
    void whenNoAiProviderIsConfiguredGenerateIsRefusedWithTheGenericMessageAndNothingIsQueued() {
        Env env = new Env("NONE", false);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.PYTHON, AutomationPattern.SIMPLE);

        AutomationApiException e = rejected(() -> env.submit(env.newTestCases(1)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertEquals("AI_UNAVAILABLE", e.getErrorCode());
        assertEquals("Layanan AI sedang tidak tersedia. Silakan coba lagi nanti.", e.getMessage());
        assertEquals(0, env.generations.rows.size());
        assertFalse(env.service.getUsage(EMAIL, env.project.getId()).isAiEnabled()); // FE menonaktifkan tombol
    }

    @Test
    void theNumberOfTestCasesPerGenerationFollowsTheTierConfiguration() {
        Env env = readyEnv();
        List<UUID> six = env.newTestCases(6);

        AutomationApiException e = rejected(() -> env.submit(six));
        assertEquals("TOO_MANY_TEST_CASES", e.getErrorCode());
        assertTrue(e.getMessage().contains("5"));
        assertEquals(0, env.generations.rows.size());

        env.userType = "VIP_YEARLY"; // tier VIP: maks 20
        assertEquals(AutomationGenerationStatus.QUEUED, env.submit(six).getStatus());
    }

    @Test
    void duplicateIdsAreCountedOnceAndForeignOrUnknownOrArchivedTestCasesAreRejected() {
        Env env = readyEnv();
        List<UUID> ids = env.newTestCases(1);

        // id ganda dihitung satu
        AutomationGenerationCreatedDTO created = env.submit(List.of(ids.get(0), ids.get(0), ids.get(0)));
        assertEquals(1, env.generations.rows.get(created.getId()).getTestCaseCount());
        env.runWorker();

        // id tak dikenal
        assertEquals("INVALID_TEST_CASE_SELECTION", rejected(() -> env.submit(List.of(UUID.randomUUID()))).getErrorCode());

        // test case milik project lain
        TestCase foreign = AutomationTestSupport.testCase(env.otherProject, "Asing", null, null);
        env.testCases.put(foreign.getId(), foreign);
        assertEquals("INVALID_TEST_CASE_SELECTION", rejected(() -> env.submit(List.of(foreign.getId()))).getErrorCode());

        // test case diarsipkan
        env.testCases.get(ids.get(0)).setStatus(TestCaseStatus.ARCHIVED);
        assertEquals("INVALID_TEST_CASE_SELECTION", rejected(() -> env.submit(ids)).getErrorCode());
    }

    @Test
    void dailyLimitIsPerUserAndTierSpecificWithAnActionableMessage() {
        Env env = readyEnv();
        for (int i = 0; i < 3; i++) {           // FREE: 3 per hari
            env.submit(env.newTestCases(1));
            env.runWorker();
        }
        assertEquals(3, env.service.getUsage(EMAIL, env.project.getId()).getUsedToday());

        AutomationApiException e = rejected(() -> env.submit(env.newTestCases(1)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        assertEquals("AI_PLAN_LIMIT_REACHED", e.getErrorCode());
        assertTrue(e.getMessage().contains("Batas generate harian") && e.getMessage().contains("3"));

        env.userType = "VIP_MONTHLY";           // VIP: 30 per hari -> lolos
        assertEquals(AutomationGenerationStatus.QUEUED, env.submit(env.newTestCases(1)).getStatus());
        env.runWorker();
        env.userType = "FREE";

        env.clock.advanceHours(24);             // hari berikutnya: jatah segar
        assertEquals(0, env.service.getUsage(EMAIL, env.project.getId()).getUsedToday());
        assertEquals(AutomationGenerationStatus.QUEUED, env.submit(env.newTestCases(1)).getStatus());
    }

    @Test
    void onlyOneActiveJobPerUserAtATime() {
        Env env = readyEnv();
        env.submit(env.newTestCases(1));       // QUEUED, worker belum jalan

        AutomationApiException e = rejected(() -> env.submit(env.newTestCases(1)));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("GENERATION_IN_PROGRESS", e.getErrorCode());

        env.runWorker();                        // selesai -> boleh lagi
        assertEquals(AutomationGenerationStatus.QUEUED, env.submit(env.newTestCases(1)).getStatus());
    }

    @Test
    void theSystemWideDailyCapIsAnOperationalLimitShownAsTheGenericMessage() {
        Env env = readyEnv();
        env.props.setGlobalDailyLimit(2);
        env.props.getFree().setDailyLimit(0); // tanpa batas per user: yang menahan hanya batas global
        env.submit(env.newTestCases(1));
        env.runWorker();
        env.submit(env.newTestCases(1));
        env.runWorker();

        AutomationApiException e = rejected(() -> env.submit(env.newTestCases(1)));

        assertEquals("AI_UNAVAILABLE", e.getErrorCode());          // bukan "batas Anda tercapai": bukan salah user
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }

    // =================== Kegagalan AI ===================

    private static final class LogCapture implements AutoCloseable {
        final ch.qos.logback.classic.Logger logger;
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        LogCapture(Class<?> type) {
            logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type);
            appender.start();
            logger.addAppender(appender);
        }

        List<ILoggingEvent> errors() {
            return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
        }
    }

    @Test
    void everyKindOfProviderFailureGivesTheSameGenericMessageAndIsLoggedAtErrorLevel() {
        for (String mode : List.of("QUOTA_EXHAUSTED", "RATE_LIMITED", "UNAVAILABLE", "TIMEOUT", "REJECTED")) {
            Env env = new Env(mode, true);
            env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE);
            AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));

            try (LogCapture log = new LogCapture(AutomationGenerationWorker.class)) {
                env.runWorker();

                AutomationGenerationResponseDTO failed = env.get(created.getId());
                assertEquals(AutomationGenerationStatus.FAILED, failed.getStatus(), mode);
                assertEquals("AI_UNAVAILABLE", failed.getErrorCode(), mode);
                assertEquals("Layanan AI sedang tidak tersedia. Silakan coba lagi nanti.", failed.getErrorMessage(), mode);
                assertTrue(failed.getFiles().isEmpty(), mode);
                assertTrue(log.errors().size() >= 1, "harus tercatat di log ERROR: " + mode);
                assertTrue(log.errors().get(0).getFormattedMessage().contains(mode), "log memuat jenis kegagalan: " + mode);
            }
        }
    }

    @Test
    void theGenericMessageNeverRevealsQuotaBillingOrProviderDetails() {
        Env env = new Env("QUOTA_EXHAUSTED", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE);
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));
        env.runWorker();

        String shown = env.get(created.getId()).getErrorMessage().toLowerCase();
        String code = env.get(created.getId()).getErrorCode().toLowerCase();

        for (String forbidden : List.of("kuota", "quota", "saldo", "billing", "credit", "fake", "provider", "rate")) {
            assertFalse(shown.contains(forbidden), "pesan memuat kata terlarang: " + forbidden);
        }
        assertFalse(code.contains("quota"));
    }

    @Test
    void providerFailuresDoNotConsumeTheUsersDailyAllowance() {
        Env env = new Env("UNAVAILABLE", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE);
        for (int i = 0; i < 6; i++) {            // jauh melebihi batas harian FREE (3)
            env.submit(env.newTestCases(1));
            env.runWorker();
        }

        assertEquals(0, env.service.getUsage(EMAIL, env.project.getId()).getUsedToday());
        assertEquals(6, env.generations.rows.size());
        assertTrue(env.generations.all().stream().allMatch(g -> g.getStatus() == AutomationGenerationStatus.FAILED));
    }

    @Test
    void afterQuotaExhaustionNewRequestsAreRefusedImmediatelyWithoutCreatingJobsOrCallingTheProvider() {
        Env env = new Env("QUOTA_EXHAUSTED", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE);
        env.submit(env.newTestCases(1));
        env.runWorker();                                  // job pertama membuka circuit breaker
        int jobsBefore = env.generations.rows.size();

        AutomationApiException e = rejected(() -> env.submit(env.newTestCases(1)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertEquals("AI_UNAVAILABLE", e.getErrorCode());
        assertEquals(jobsBefore, env.generations.rows.size()); // tidak ada job baru
        env.clock.advanceHours(1);                        // circuit tertutup lagi -> permintaan diterima
        assertEquals(AutomationGenerationStatus.QUEUED, env.submit(env.newTestCases(1)).getStatus());
    }

    @Test
    void unusableAiOutputFailsWithItsOwnMessageKeepsTokenAccountingAndDoesNotCountTowardsTheQuota() {
        Env env = new Env("INVALID_OUTPUT", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE);
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));

        env.runWorker();

        AutomationGenerationResponseDTO failed = env.get(created.getId());
        assertEquals("AI_INVALID_OUTPUT", failed.getErrorCode());
        assertEquals("Hasil generate tidak dapat diproses. Silakan coba lagi.", failed.getErrorMessage());
        AutomationGeneration row = env.generations.rows.get(created.getId());
        assertTrue(row.getInputTokens() > 0, "token tetap dicatat walau hasilnya tidak bisa dipakai");
        assertNull(row.getResultFiles());
        assertEquals(0, env.service.getUsage(EMAIL, env.project.getId()).getUsedToday());
    }

    // =================== Riwayat, detail, unduh ===================

    @Test
    void historyIsNewestFirstLimitedToTwentyAndWithoutFileContents() {
        Env env = readyEnv();
        env.props.getFree().setDailyLimit(0);
        java.time.LocalDateTime base = java.time.LocalDateTime.of(2026, 10, 5, 8, 0);
        for (int i = 0; i < 22; i++) {
            AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));
            env.runWorker();
            env.generations.rows.get(created.getId()).setCreatedAt(base.plusMinutes(i)); // urutan waktu yang jelas
        }

        var history = env.service.listGenerations(EMAIL, env.project.getId());

        assertEquals(20, history.size());
        assertEquals(base.plusMinutes(21), history.get(0).getCreatedAt());   // terbaru dulu
        assertEquals(base.plusMinutes(2), history.get(19).getCreatedAt());   // 2 yang tertua terpotong
        assertEquals(AutomationGenerationStatus.SUCCEEDED, history.get(0).getStatus());
        assertEquals(EMAIL, history.get(0).getRequestedBy());
    }

    @Test
    void downloadOfAFailedRunIsRefusedAndAnotherProjectsRunIsNotFound() {
        Env env = new Env("TIMEOUT", true);
        env.configure(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE);
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));
        env.runWorker();

        AutomationApiException notReady = rejected(() -> env.service.download(EMAIL, env.project.getId(), created.getId()));
        assertEquals("GENERATION_NOT_READY", notReady.getErrorCode());

        // id valid tetapi dicari lewat project lain -> 404 (tidak membocorkan keberadaannya)
        assertThrows(ProjectNotFoundException.class, () -> env.service.getGeneration(EMAIL, env.otherProject.getId(), created.getId()));
        AutomationApiException unknown = rejected(() -> env.service.getGeneration(EMAIL, env.project.getId(), UUID.randomUUID()));
        assertEquals(HttpStatus.NOT_FOUND, unknown.getStatus());
    }

    // =================== Penggunaan (usage) ===================

    @Test
    void usageReportsTierLimitsAndNeverExposesProviderOrModel() {
        Env env = readyEnv();
        env.props.getFree().setModel("model-rahasia-free");
        env.userType = "VIP_YEARLY";

        var usage = env.service.getUsage(EMAIL, env.project.getId());

        assertTrue(usage.isAiEnabled());
        assertEquals("VIP", usage.getTier());
        assertEquals(20, usage.getMaxTestCasesPerGeneration());
        assertEquals(30, usage.getDailyLimit());
        assertFalse(usage.toString().contains("rahasia"));
    }

    // =================== Pemulihan startup & dispatcher ===================

    @Test
    void recoveryMarksQueuedAndRunningAsInterruptedAndFreesTheUserToTryAgain() {
        Env env = readyEnv();
        env.props.getFree().setDailyLimit(0);
        AutomationGenerationCreatedDTO done = env.submit(env.newTestCases(1));
        env.runWorker();
        AutomationGenerationCreatedDTO queued = env.submit(env.newTestCases(1));
        env.generations.rows.get(queued.getId()).setStatus(AutomationGenerationStatus.RUNNING); // mati di tengah proses

        int recovered = new AutomationStartupRecovery(env.generations.repository(), env.clock).recover();

        assertEquals(1, recovered);
        assertEquals(AutomationGenerationStatus.SUCCEEDED, env.get(done.getId()).getStatus());
        AutomationGenerationResponseDTO interrupted = env.get(queued.getId());
        assertEquals(AutomationGenerationStatus.FAILED, interrupted.getStatus());
        assertEquals("GENERATION_INTERRUPTED", interrupted.getErrorCode());
        assertEquals(AutomationGenerationStatus.QUEUED, env.submit(env.newTestCases(1)).getStatus()); // tidak terblokir lagi
    }

    @Test
    void theDispatcherWaitsForTheTransactionToCommitBeforeStartingTheWorker() throws Exception {
        Env env = readyEnv();
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));
        env.dispatched.clear();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorAutomationJobDispatcher dispatcher = new ExecutorAutomationJobDispatcher(executor, env.worker);

        TransactionSynchronizationManager.initSynchronization();
        try {
            dispatcher.dispatch(created.getId());
            Thread.sleep(150);
            assertEquals(AutomationGenerationStatus.QUEUED, env.get(created.getId()).getStatus()); // belum commit: belum jalan

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(AutomationGenerationStatus.SUCCEEDED, env.get(created.getId()).getStatus());
    }

    @Test
    void aFullQueueFailsTheJobWithAServerBusyMessageInsteadOfLeavingItHanging() throws Exception {
        Env env = readyEnv();
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));
        env.dispatched.clear();
        ThreadPoolExecutor saturated = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch release = new CountDownLatch(1);
        saturated.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        new ExecutorAutomationJobDispatcher(saturated, env.worker).dispatch(created.getId());

        AutomationGenerationResponseDTO busy = env.get(created.getId());
        assertEquals(AutomationGenerationStatus.FAILED, busy.getStatus());
        assertEquals("SERVER_BUSY", busy.getErrorCode());
        assertEquals(0, env.service.getUsage(EMAIL, env.project.getId()).getUsedToday());
        release.countDown();
        saturated.shutdownNow();
    }

    @Test
    void aJobIsNeverProcessedTwice() {
        Env env = readyEnv();
        AutomationGenerationCreatedDTO created = env.submit(env.newTestCases(1));
        env.runWorker();
        AutomationGeneration row = env.generations.rows.get(created.getId());
        java.time.LocalDateTime startedAt = row.getStartedAt();
        java.time.LocalDateTime finishedAt = row.getFinishedAt();
        Integer inputTokens = row.getInputTokens();

        env.clock.advanceHours(3);          // kalau diproses ulang, waktu mulai/selesai akan bergeser
        env.worker.run(created.getId());   // dipanggil lagi (mis. dispatch ganda)

        assertEquals(AutomationGenerationStatus.SUCCEEDED, row.getStatus());
        assertEquals(startedAt, row.getStartedAt());
        assertEquals(finishedAt, row.getFinishedAt());
        assertEquals(inputTokens, row.getInputTokens());
    }
}
