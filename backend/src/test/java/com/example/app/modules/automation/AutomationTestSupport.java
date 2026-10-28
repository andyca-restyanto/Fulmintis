// filepath: /backend/src/test/java/com/example/app/modules/automation/AutomationTestSupport.java
package com.example.app.modules.automation;

import com.example.app.modules.automation.entity.AutomationGeneration;
import com.example.app.modules.automation.entity.AutomationSetup;
import com.example.app.modules.automation.repository.AutomationGenerationRepository;
import com.example.app.modules.automation.repository.AutomationSetupRepository;
import com.example.app.modules.project.entity.Project;
import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.entity.TestCase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pendukung test modul automation: jam yang bisa digeser dan repository dalam memori (tanpa DB, tanpa Mockito). */
public final class AutomationTestSupport {

    private AutomationTestSupport() {
    }

    public static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /** Jam yang bisa digeser; zona UTC supaya batas hari deterministik. */
    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(String isoInstant) {
            this.now = Instant.parse(isoInstant);
        }

        public void advanceHours(long hours) {
            now = now.plusSeconds(hours * 3600);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public static TestCase testCase(Project project, String title, String steps, String expected) {
        return TestCase.builder()
                .id(UUID.randomUUID())
                .project(project)
                .title(title)
                .priority(TestCasePriority.HIGH)
                .type(TestCaseType.MANUAL)
                .scenarioType(TestCaseScenarioType.POSITIVE)
                .precondition("User sudah login")
                .testStep(steps)
                .expectedResult(expected)
                .status(TestCaseStatus.ACTIVE)
                .build();
    }

    /** Repository riwayat generate dalam memori (perilaku query disalin dari kontrak di repository asli). */
    public static final class GenerationStore {
        public final Map<UUID, AutomationGeneration> rows = new LinkedHashMap<>();
        private final Clock clock;

        public GenerationStore(Clock clock) {
            this.clock = clock;
        }

        public AutomationGenerationRepository repository() {
            return (AutomationGenerationRepository) Proxy.newProxyInstance(
                    AutomationGenerationRepository.class.getClassLoader(),
                    new Class<?>[]{AutomationGenerationRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save", "saveAndFlush" -> persist((AutomationGeneration) args[0]);
                        case "findById" -> Optional.ofNullable(rows.get((UUID) args[0]));
                        case "findByIdAndProjectId" -> Optional.ofNullable(rows.get((UUID) args[0]))
                                .filter(g -> g.getProject().getId().equals(args[1]));
                        case "findByProjectIdOrderByCreatedAtDesc" -> {
                            int size = ((org.springframework.data.domain.Pageable) args[1]).getPageSize();
                            yield rows.values().stream()
                                    .filter(g -> g.getProject().getId().equals(args[0]))
                                    .sorted(Comparator.comparing(AutomationGeneration::getCreatedAt).reversed())
                                    .limit(size).toList();
                        }
                        case "existsByRequestedByAndStatusIn" -> rows.values().stream()
                                .anyMatch(g -> g.getRequestedBy().equals(args[0]) && ((Collection<?>) args[1]).contains(g.getStatus()));
                        case "countByRequestedByAndStatusInAndCreatedAtGreaterThanEqual" -> rows.values().stream()
                                .filter(g -> g.getRequestedBy().equals(args[0]) && ((Collection<?>) args[1]).contains(g.getStatus())
                                        && !g.getCreatedAt().isBefore((LocalDateTime) args[2])).count();
                        case "countByStatusInAndCreatedAtGreaterThanEqual" -> rows.values().stream()
                                .filter(g -> ((Collection<?>) args[0]).contains(g.getStatus())
                                        && !g.getCreatedAt().isBefore((LocalDateTime) args[1])).count();
                        case "failActiveJobs" -> {
                            int count = 0;
                            for (AutomationGeneration g : rows.values()) {
                                if (((Collection<?>) args[0]).contains(g.getStatus())) {
                                    g.setStatus((AutomationGenerationStatus) args[1]);
                                    g.setErrorCode((String) args[2]);
                                    g.setErrorMessage((String) args[3]);
                                    g.setFinishedAt((LocalDateTime) args[4]);
                                    count++;
                                }
                            }
                            yield count;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private AutomationGeneration persist(AutomationGeneration generation) {
            if (generation.getId() == null) {
                generation.setId(UUID.randomUUID());
                generation.setCreatedAt(LocalDateTime.now(clock));
            }
            rows.put(generation.getId(), generation);
            return generation;
        }

        public List<AutomationGeneration> all() {
            return new ArrayList<>(rows.values());
        }
    }

    /** Repository setup dalam memori; {@code failFlushWithConflict=true} mensimulasikan pelanggaran unique. */
    public static final class SetupStore {
        public final Map<UUID, AutomationSetup> byProject = new LinkedHashMap<>();
        public boolean failFlushWithConflict;

        public AutomationSetupRepository repository() {
            return (AutomationSetupRepository) Proxy.newProxyInstance(
                    AutomationSetupRepository.class.getClassLoader(),
                    new Class<?>[]{AutomationSetupRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByProjectId" -> Optional.ofNullable(byProject.get((UUID) args[0]));
                        case "save", "saveAndFlush" -> {
                            if (failFlushWithConflict && method.getName().equals("saveAndFlush")) {
                                throw new org.springframework.dao.DataIntegrityViolationException("uq_automation_setup_project");
                            }
                            AutomationSetup setup = (AutomationSetup) args[0];
                            if (setup.getId() == null) {
                                setup.setId(UUID.randomUUID());
                            }
                            byProject.put(setup.getProject().getId(), setup);
                            yield setup;
                        }
                        case "delete" -> {
                            byProject.values().remove((AutomationSetup) args[0]);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
