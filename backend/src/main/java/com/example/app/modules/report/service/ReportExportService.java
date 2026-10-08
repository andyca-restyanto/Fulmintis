// backend/src/main/java/com/example/app/modules/report/service/ReportExportService.java
package com.example.app.modules.report.service;

import java.util.UUID;

public interface ReportExportService {

    /**
     * Requirement #3-4: generate 1 file .xlsx berisi seluruh report project
     * ini -- sheet "Summary" (angka ringkasan + diagram distribusi), sheet
     * "All Results" (list semua test case dari semua test run, flat), dan
     * 1 sheet terpisah PER test run (nama sheet = judul test run). Format
     * ini SENGAJA disamakan dgn contoh file yang diberikan user (lihat
     * CHANGES.md utk 2 perbedaan yg sengaja diambil drpd meniru mentah2 --
     * baris "Skipped" yg tidak ada konsepnya di app ini, dan kolom Status
     * per baris yg TIDAK digabung New+Pending spt di Summary).
     *
     * @return isi file .xlsx sbg byte array, siap ditulis ke response body
     *         (lihat ReportController.exportExcel()).
     * @throws com.example.app.modules.project.exception.ProjectNotFoundException
     *         kalau project tidak ada / user bukan member (404).
     */
    byte[] exportProjectReport(String userEmail, UUID projectId);
}
