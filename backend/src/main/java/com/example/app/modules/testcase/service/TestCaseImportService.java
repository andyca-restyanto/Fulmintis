// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/TestCaseImportService.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.dto.TestCaseImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface TestCaseImportService {

    /**
     * Import test case dari Excel ke satu folder. OWNER maupun COLLABORATOR
     * boleh (sama seperti membuat test case); bukan member -> 404.
     * <ul>
     *   <li>{@code dryRun=true}: hanya memeriksa & memetakan, TIDAK menyimpan.</li>
     *   <li>{@code dryRun=false}: menyimpan SEMUA baris dalam satu transaksi,
     *       atau tidak sama sekali kalau ada baris bermasalah
     *       ({@code TestCaseImportValidationException}).</li>
     * </ul>
     *
     * @throws com.example.app.modules.testcase.exception.InvalidImportFileException file tidak bisa diproses
     * @throws com.example.app.modules.testrepository.exception.TestFolderNotFoundException folder bukan milik project
     */
    TestCaseImportResultDTO importTestCases(
            String userEmail, UUID projectId, UUID folderId, MultipartFile file, boolean dryRun);

    /** File template .xlsx (statis). Wajib member project -- sama seperti endpoint lain di project ini. */
    byte[] buildTemplate(String userEmail, UUID projectId);
}
