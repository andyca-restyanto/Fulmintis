// backend/src/main/java/com/example/app/modules/report/service/ReportService.java
package com.example.app.modules.report.service;

import com.example.app.modules.report.dto.ReportOverviewResponseDTO;
import com.example.app.modules.testrun.dto.TestRunDetailResponseDTO;

import java.util.List;
import java.util.UUID;

public interface ReportService {

    /**
     * Data tab Overview menu Report (requirement #1-3). Boleh diakses OWNER
     * MAUPUN COLLABORATOR project ini (sama seperti listing Test Run) --
     * lihat javadoc {@link com.example.app.modules.report.dto.ReportOverviewResponseDTO}
     * untuk definisi tiap angka.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member project ini (404,
     *         disamarkan supaya tidak bocor keberadaan project ke non-member).
     */
    ReportOverviewResponseDTO getOverview(String userEmail, UUID projectId);

    /**
     * Data tab Run Details (requirement #4 lama sekarang dikerjakan): list
     * SEMUA test run di project ini, masing-masing lengkap dgn testResults
     * (detail tiap test case yang di-mapping + hasil eksekusinya) supaya FE
     * bisa expand 1 baris test run tanpa panggil API lagi. Delegasi penuh ke
     * {@link com.example.app.modules.testrun.service.TestRunService#listTestRunDetails}
     * -- lihat javadoc di sana utk alasan kenapa mapping-nya tidak
     * diduplikasi di modul report.
     *
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     */
    List<TestRunDetailResponseDTO> getRunDetails(String userEmail, UUID projectId);
}
