// filepath: /backend/src/test/java/com/example/app/modules/testcase/service/TestCaseAiTestSupport.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import com.example.app.modules.testcase.entity.TestCaseAiGeneration;
import com.example.app.modules.testcase.repository.TestCaseAiGenerationRepository;
import com.example.app.shared.ai.AiClient;
import com.example.app.shared.ai.AiProviderException;
import com.example.app.shared.ai.AiRequest;
import com.example.app.shared.ai.AiResponse;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Pendukung test generate test case: repository dalam memori dan klien AI yang skrip-nya bisa diatur (tanpa DB, tanpa Mockito). */
final class TestCaseAiTestSupport {

    private TestCaseAiTestSupport() {
    }

    /** Klien AI yang membalas sesuai skrip dan merekam setiap permintaan yang diterimanya. */
    static final class ScriptedClient implements AiClient {
        final List<AiRequest> requests = new ArrayList<>();
        Function<AiRequest, String> script = request -> "{}";
        AiProviderException failure;

        @Override
        public String providerId() {
            return "scripted";
        }

        @Override
        public AiResponse generate(AiRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return new AiResponse(script.apply(request), 120, 340, request.model());
        }
    }

    /** Repository riwayat generate dalam memori (perilaku query disalin dari kontrak di repository asli). */
    static final class GenerationStore {
        final Map<UUID, TestCaseAiGeneration> rows = new LinkedHashMap<>();
        private final Clock clock;

        GenerationStore(Clock clock) {
            this.clock = clock;
        }

        TestCaseAiGenerationRepository repository() {
            return (TestCaseAiGenerationRepository) Proxy.newProxyInstance(
                    TestCaseAiGenerationRepository.class.getClassLoader(),
                    new Class<?>[]{TestCaseAiGenerationRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save", "saveAndFlush" -> persist((TestCaseAiGeneration) args[0]);
                        case "findById" -> Optional.ofNullable(rows.get((UUID) args[0]));
                        case "findByIdAndProjectIdAndRequestedBy" -> Optional.ofNullable(rows.get((UUID) args[0]))
                                .filter(g -> g.getProject().getId().equals(args[1]) && g.getRequestedBy().equals(args[2]));
                        case "findFirstByProjectIdAndRequestedByAndStatusAndCommittedFalseAndDraftsJsonIsNotNullOrderByCreatedAtDesc" ->
                                rows.values().stream()
                                        .filter(g -> g.getProject().getId().equals(args[0]) && g.getRequestedBy().equals(args[1])
                                                && g.getStatus() == args[2] && !g.isCommitted() && g.getDraftsJson() != null)
                                        .max(java.util.Comparator.comparing(TestCaseAiGeneration::getCreatedAt));
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
                            for (TestCaseAiGeneration g : rows.values()) {
                                if (((Collection<?>) args[0]).contains(g.getStatus())) {
                                    g.setStatus((TestCaseAiGenerationStatus) args[1]);
                                    g.setErrorCode((String) args[2]);
                                    g.setErrorMessage((String) args[3]);
                                    g.setFinishedAt((LocalDateTime) args[4]);
                                    count++;
                                }
                            }
                            yield count;
                        }
                        case "claimForCommit" -> claim((UUID) args[0], (LocalDateTime) args[1], (Integer) args[2], (TestCaseAiGenerationStatus) args[3]);
                        case "clearExpiredDrafts" -> {
                            int count = 0;
                            for (TestCaseAiGeneration g : rows.values()) {
                                if (g.getDraftsJson() != null && !g.isCommitted() && g.getCreatedAt().isBefore((LocalDateTime) args[0])) {
                                    g.setDraftsJson(null);
                                    count++;
                                }
                            }
                            yield count;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        /** Atomik, seperti UPDATE ... WHERE committed = false di database: hanya satu pemanggil yang mendapat 1. */
        private synchronized int claim(UUID id, LocalDateTime now, int count, TestCaseAiGenerationStatus succeeded) {
            TestCaseAiGeneration g = rows.get(id);
            if (g == null || g.isCommitted() || g.getStatus() != succeeded || g.getDraftsJson() == null) {
                return 0;
            }
            g.setCommitted(true);
            g.setCommittedAt(now);
            g.setCommittedCount(count);
            g.setDraftsJson(null);
            return 1;
        }

        private synchronized TestCaseAiGeneration persist(TestCaseAiGeneration generation) {
            if (generation.getId() == null) {
                generation.setId(UUID.randomUUID());
                generation.setCreatedAt(LocalDateTime.now(clock));
            }
            rows.put(generation.getId(), generation);
            return generation;
        }

        List<TestCaseAiGeneration> all() {
            return new ArrayList<>(rows.values());
        }
    }
}
