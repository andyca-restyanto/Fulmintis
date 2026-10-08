// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseImportResultDTO.java
package com.example.app.modules.testcase.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Hasil import test case dari Excel (pratinjau maupun simpan). Match 100%
 * dgn TestCaseImportResult (FE).
 * <ul>
 *   <li>{@code dryRun=true}: TIDAK ada yang disimpan ({@code importedCount} = 0);
 *       baris bermasalah muncul di {@code errors} dan respons tetap 200.</li>
 *   <li>{@code dryRun=false} tanpa error: SEMUA baris valid disimpan;
 *       {@code importedCount} = jumlah test case yang dibuat.</li>
 *   <li>{@code dryRun=false} dengan error: tidak ada yang disimpan, respons
 *       400 dgn body yang sama (semua atau tidak sama sekali).</li>
 * </ul>
 * {@code totalRows} = baris data tidak kosong (baris contoh dari template
 * tidak dihitung); {@code validRows} = yang lolos validasi.
 */
@Getter
@Builder
public class TestCaseImportResultDTO {
    private UUID folderId;
    private String folderName;
    private boolean dryRun;
    private int totalRows;
    private int validRows;
    private int importedCount;
    private List<ImportColumnMappingDTO> columnMapping;
    // Header Excel yang tidak dikenali (diabaikan).
    private List<String> unmappedColumns;
    private List<ImportIssueDTO> errors;
    private List<ImportIssueDTO> warnings;
    // Maks 20 baris valid pertama.
    private List<ImportPreviewRowDTO> preview;
}
