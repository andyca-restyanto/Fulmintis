// backend/src/main/java/com/example/app/modules/testrun/dto/UpdateTestResultRequestDTO.java
package com.example.app.modules.testrun.dto;

import com.example.app.modules.testrun.TestResultStatus;
import com.example.app.modules.testrun.TestStepResultStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Body request utk PATCH .../test-runs/{testRunId}/test-results/{testResultId}
 * -- catat hasil eksekusi 1 test case di dalam test run (requirement #5:
 * kolom status & comment pada table test_result). Evidence-nya PISAH lewat
 * endpoint upload multipart tersendiri (lihat TestRunController), bukan
 * lewat DTO JSON ini.
 */
@Getter
@Setter
public class UpdateTestResultRequestDTO {

    @NotNull(message = "Status wajib dipilih")
    private TestResultStatus status;

    private String comment;

    // Hasil per-step (tombol centang/silang di halaman Execute). OPSIONAL:
    // null = JANGAN ubah hasil per-step yang sudah tersimpan (mis. request
    // yang cuma ganti status/comment), list kosong = hapus semua tanda step.
    @Size(max = 200, message = "Maksimal 200 step")
    private List<TestStepResultStatus> stepResults;

    // OPSIONAL. Versi test result yang terakhir dilihat klien (dari
    // TestResultResponseDTO.version). Kalau dikirim & sudah tidak sama ->
    // 409. null = tanpa cek konflik (perilaku lama).
    private Long version;
}
