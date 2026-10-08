// backend/src/main/java/com/example/app/modules/testrun/dto/CreateTestRunRequestDTO.java
package com.example.app.modules.testrun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Body request utk POST /api/projects/{projectId}/test-runs (requirement
 * #1). Sesuai flow yang diminta (step 2-6): user isi title & description
 * DULU, baru pilih test case yang mau di-run -- SEMUANYA dikirim dalam 1x
 * submit "Create Test Run" (bukan 2 API call terpisah), makanya
 * testCaseIds ada di DTO create ini juga, bukan cuma di
 * AddTestCasesToTestRunRequestDTO.
 */
@Getter
@Setter
public class CreateTestRunRequestDTO {

    @NotBlank(message = "Title wajib diisi")
    @Size(max = 255, message = "Title maksimal 255 karakter")
    private String title;

    private String description;

    // Opsional di level validasi (boleh null/kosong -- test run tetap valid
    // dibuat tanpa test case, lalu ditambahkan belakangan lewat endpoint
    // POST .../test-cases, requirement #7). TAPI flow yang diminta user
    // SELALU mengisi ini sebelum klik "Create Test Run" (step 4-5).
    // Duplikat id di list ini otomatis di-skip (lihat TestRunServiceImpl).
    private List<UUID> testCaseIds;
}
