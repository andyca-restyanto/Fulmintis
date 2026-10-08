// backend/src/main/java/com/example/app/modules/report/controller/ReportController.java
package com.example.app.modules.report.controller;

import com.example.app.modules.report.dto.ReportOverviewResponseDTO;
import com.example.app.modules.report.service.ReportExportService;
import com.example.app.modules.report.service.ReportService;
import com.example.app.modules.testrun.dto.TestRunDetailResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * PROTECTED (wajib JWT valid) -- nested di bawah /api/projects/{projectId},
 * tidak di-permitAll() di SecurityConfig jadi otomatis kena
 * anyRequest().authenticated(), sama seperti TestRunController.
 * <p>
 * Menu "Report": tab "Overview" ({@code GET /overview}) & tab "Run Details"
 * ({@code GET /run-details}) dilayani endpoint di sini. Tombol "Export
 * Excel" ({@code GET /export}) menghasilkan 1 file .xlsx berisi kedua tab
 * itu sekaligus rincian per test run -- lihat javadoc
 * {@link com.example.app.modules.report.service.ReportExportService}.
 * Alur "Add New Test Run" dari menu Report memakai endpoint Test Run yang
 * SUDAH ADA (POST /test-runs, lihat TestRunController) -- tidak ada
 * endpoint create-test-run baru di modul report ini.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/report")
@RequiredArgsConstructor
public class ReportController {

    // Sama dgn nama contoh file yang dipakai sbg acuan format ("Test_Report_
    // 20260929_110335.xlsx") supaya konsisten dgn apa yang sudah dilihat user.
    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    @GetMapping("/overview")
    public ReportOverviewResponseDTO getOverview(@PathVariable UUID projectId, Authentication authentication) {
        String email = authentication.getName();
        return reportService.getOverview(email, projectId);
    }

    /**
     * Tab "Run Details": daftar SEMUA test run di project ini (terbaru
     * dulu), masing-masing sudah lengkap dgn {@code testResults} supaya FE
     * bisa expand 1 baris test run (requirement #2) tanpa panggil API lain.
     */
    @GetMapping("/run-details")
    public List<TestRunDetailResponseDTO> getRunDetails(@PathVariable UUID projectId, Authentication authentication) {
        String email = authentication.getName();
        return reportService.getRunDetails(email, projectId);
    }

    /**
     * Download laporan lengkap project ini sbg 1 file .xlsx (requirement
     * #3-4): sheet "Summary" (ringkasan + diagram), sheet "All Results"
     * (flat, semua test run), dan 1 sheet per test run. "attachment" (bukan
     * "inline") supaya browser langsung memicu Save As, bukan mencoba
     * menampilkannya.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(@PathVariable UUID projectId, Authentication authentication) {
        String email = authentication.getName();
        byte[] workbookBytes = reportExportService.exportProjectReport(email, projectId);

        String filename = "Test_Report_" + LocalDateTime.now().format(FILENAME_TIMESTAMP_FORMAT) + ".xlsx";
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbookBytes);
    }
}
