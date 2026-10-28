// backend/src/main/java/com/example/app/modules/testrun/dto/SyncTestRunCasesRequestDTO.java
package com.example.app.modules.testrun.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Body request utk PUT /api/projects/{projectId}/test-runs/{testRunId}/test-cases
 * -- "daftar AKHIR" test case yang harus ada di run ini, sesuai checklist di
 * UI Manage Cases: test case yang tercentang & belum ada di run -> ditambah,
 * yang sudah ada di run tapi tidak lagi tercentang -> dilepas (uncheck =
 * delete mapping).
 * <p>
 * BEDA dgn {@link AddTestCasesToTestRunRequestDTO}: list BOLEH kosong
 * (artinya "kosongkan run ini"), tapi TIDAK BOLEH null -- null dianggap
 * request rusak, bukan "kosongkan", supaya tidak ada yang tanpa sengaja
 * menghapus semua mapping cuma krn field-nya lupa dikirim.
 */
@Getter
@Setter
public class SyncTestRunCasesRequestDTO {

    @NotNull(message = "testCaseIds wajib dikirim (boleh kosong)")
    private List<UUID> testCaseIds;
}
