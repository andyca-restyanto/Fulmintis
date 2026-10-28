// backend/src/main/java/com/example/app/modules/testcase/specification/TestCaseSpecifications.java
package com.example.app.modules.testcase.specification;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseStatus;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.entity.TestCase;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * Tiap method balikin Specification yang boleh NULL kalau parameternya
 * kosong -- Specification.where(...).and(...) di TestCaseServiceImpl
 * otomatis skip predicate yang null, jadi setiap filter di sini beneran
 * OPSIONAL (requirement #6: user boleh search/filter kombinasi apapun,
 * termasuk tanpa filter sama sekali).
 */
public final class TestCaseSpecifications {

    private TestCaseSpecifications() {
    }

    public static Specification<TestCase> hasProjectId(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<TestCase> hasFolderId(UUID folderId) {
        return (root, query, cb) -> folderId == null ? null : cb.equal(root.get("folder").get("id"), folderId);
    }

    // Search by title (partial, case-insensitive) ATAU by id (exact match,
    // requirement Test Run #3: "search test case with name / id" saat user
    // mapping test case ke test run). Dipakai ulang oleh endpoint search
    // test case yang sudah ada (GET /test-cases) -- tidak perlu endpoint
    // baru khusus utk kebutuhan mapping test run.
    public static Specification<TestCase> titleOrIdContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String trimmed = keyword.trim();
            var titlePredicate = cb.like(cb.lower(root.get("title")), "%" + trimmed.toLowerCase() + "%");

            // UUID.fromString() melempar exception kalau bukan format UUID
            // valid -- predicate id CUMA ditambahkan (OR) kalau keyword-nya
            // memang valid UUID, supaya keyword biasa (bukan id) tetap
            // search title-only spt sebelumnya, tidak ada perubahan behavior.
            try {
                UUID id = UUID.fromString(trimmed);
                return cb.or(titlePredicate, cb.equal(root.get("id"), id));
            } catch (IllegalArgumentException notAUuid) {
                return titlePredicate;
            }
        };
    }

    public static Specification<TestCase> hasPriority(TestCasePriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    public static Specification<TestCase> hasType(TestCaseType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<TestCase> hasScenarioType(TestCaseScenarioType scenarioType) {
        return (root, query, cb) -> scenarioType == null ? null : cb.equal(root.get("scenarioType"), scenarioType);
    }

    // BEDA dgn field lain di atas: status BUKAN filter opsional dari user,
    // tapi aturan visibilitas (requirement #4) yang di-enforce paksa oleh
    // TestCaseServiceImpl -- selalu diisi ACTIVE (utk listing biasa) atau
    // ARCHIVED (utk listing khusus OWNER), tidak pernah null.
    public static Specification<TestCase> hasStatus(TestCaseStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
