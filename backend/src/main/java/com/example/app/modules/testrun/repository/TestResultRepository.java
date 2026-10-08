// backend/src/main/java/com/example/app/modules/testrun/repository/TestResultRepository.java
package com.example.app.modules.testrun.repository;

import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.entity.TestResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    // "TestRunId"/"TestCaseId" di-resolve Spring Data ke nested property
    // testRun.id / testCase.id.
    // JOIN FETCH testCase: DTO hasil selalu membaca title/priority/step dst
    // dari test case -- tanpa ini tiap baris memicu 1 query lazy (N+1).
    @EntityGraph(attributePaths = {"testCase"})
    List<TestResult> findByTestRunIdOrderByCreatedAtAsc(UUID testRunId);

    // Versi batch utk banyak run sekaligus (tab Run Details & export Excel):
    // 1 query utk SEMUA run, dikelompokkan per run di memori oleh service.
    @EntityGraph(attributePaths = {"testCase"})
    List<TestResult> findByTestRunIdInOrderByCreatedAtAsc(Collection<UUID> testRunIds);

    // Dipakai TestRunServiceImpl SEBELUM insert baru -- cegah 1 test case
    // ke-mapping dobel ke test run yang sama (di-skip diam-diam, bukan error,
    // lihat javadoc TestRunService.addTestCasesToRun()).
    boolean existsByTestRunIdAndTestCaseId(UUID testRunId, UUID testCaseId);

    // Dipakai TestRunServiceImpl.removeTestCaseFromRun() (requirement
    // tambahan #1) -- cari baris mapping test_run+test_case yang mau
    // dilepas, supaya evidence file-nya (kalau ada) bisa dihapus dari disk
    // SEBELUM baris DB-nya sendiri dihapus.
    java.util.Optional<TestResult> findByTestRunIdAndTestCaseId(UUID testRunId, UUID testCaseId);

    // Dipakai TestRunServiceImpl.deleteTestRun() -- test_result TIDAK di-set
    // cascade delete lewat JPA relation (TestRun tidak punya @OneToMany ke
    // TestResult), jadi baris test_result terkait dihapus manual dulu
    // SEBELUM baris test_run-nya dihapus (FK test_result.test_run_id NOT
    // NULL REFERENCES test_run, kalau kebalik urutannya bakal kena FK
    // violation).
    void deleteByTestRunId(UUID testRunId);

    // Hitung jumlah test case yang ke-mapping di 1 run tanpa fetch semua
    // baris test_result-nya (saat ini listing memakai hitungan in-memory di
    // TestRunServiceImpl.toResponseDTO, method ini cadangan utk query ringan).
    long countByTestRunId(UUID testRunId);

    /**
     * Jumlah test result per status dari SEMUA test run di 1 project, dalam 1
     * query GROUP BY (dipakai modul report, tab Overview). Status yang tidak
     * punya baris sama sekali TIDAK muncul di hasil -- caller yang mengisi 0.
     * Test case yang sama di 2 test run berbeda dihitung 2x (1x per run),
     * karena yang dihitung adalah HASIL EKSEKUSI, bukan test case uniknya.
     */
    @Query("""
            select r.status as status, count(r) as total
            from TestResult r
            where r.testRun.project.id = :projectId
            group by r.status
            """)
    List<StatusCount> countGroupedByStatus(@Param("projectId") UUID projectId);

    /** Projection interface utk hasil {@link #countGroupedByStatus}. */
    // Breakdown status PER RUN dalam 1 query (utk listing test run) --
    // menggantikan 1 query fetch-semua-result per run.
    @Query("""
            select r.testRun.id as runId, r.status as status, count(r) as total
            from TestResult r
            where r.testRun.project.id = :projectId
            group by r.testRun.id, r.status
            """)
    List<RunStatusCount> countGroupedByRunAndStatus(@Param("projectId") UUID projectId);

    // Sama dengan countGroupedByRunAndStatus tapi hanya utk run tertentu --
    // dashboard hanya butuh run terbaru + run yang masih terbuka, bukan semua run.
    @Query("""
            select r.testRun.id as runId, r.status as status, count(r) as total
            from TestResult r
            where r.testRun.id in :runIds
            group by r.testRun.id, r.status
            """)
    List<RunStatusCount> countGroupedByRunAndStatusForRuns(@Param("runIds") Collection<UUID> runIds);

    // ---- Dashboard project: health ----
    // Tidak ada query khusus: Current Project Health memakai countGroupedByStatus
    // (+ ResultStatusTotals) yang SAMA dengan Report, jadi angkanya identik.

    // ---- Dashboard project: execution timeline ----
    // Jumlah eksekusi per hari & status (PASSED/FAILED/BLOCKED) sejak :fromDateTime,
    // semua run di project (termasuk test case yang kemudian diarsipkan --
    // eksekusi adalah kejadian yang sudah terjadi). Hari = tanggal di zona
    // waktu server (sama dengan saat LocalDateTime.now() ditulis).
    // Hasil: [yyyy-MM-dd (String), status (String), total (Number)].
    @Query(value = """
            select to_char(coalesce(tr.executed_at, tr.updated_at, tr.created_at), 'YYYY-MM-DD') as exec_day,
                   tr.status as status,
                   count(*) as total
            from test_result tr
            join test_run r on r.id = tr.test_run_id
            where r.project_id = :projectId
              and tr.status in ('PASSED', 'FAILED', 'BLOCKED')
              and coalesce(tr.executed_at, tr.updated_at, tr.created_at) >= :fromDateTime
            group by 1, 2
            order by 1
            """, nativeQuery = true)
    List<Object[]> countExecutionsPerDay(
            @Param("projectId") UUID projectId, @Param("fromDateTime") LocalDateTime fromDateTime);

    interface RunStatusCount {
        UUID getRunId();

        TestResultStatus getStatus();

        Long getTotal();
    }

    interface StatusCount {
        TestResultStatus getStatus();

        Long getTotal();
    }
}
