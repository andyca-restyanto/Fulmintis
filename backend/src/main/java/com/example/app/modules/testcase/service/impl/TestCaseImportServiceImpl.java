// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/impl/TestCaseImportServiceImpl.java
package com.example.app.modules.testcase.service.impl;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.dto.ImportPreviewRowDTO;
import com.example.app.modules.testcase.dto.TestCaseImportResultDTO;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.exception.InvalidImportFileException;
import com.example.app.modules.testcase.exception.TestCaseImportValidationException;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testcase.service.TestCaseImportService;
import com.example.app.modules.testcase.service.importer.ImportFileGuard;
import com.example.app.modules.testcase.service.importer.ParsedImport;
import com.example.app.modules.testcase.service.importer.TestCaseExcelParser;
import com.example.app.modules.testcase.service.importer.TestCaseImportTemplateBuilder;
import com.example.app.modules.testrepository.entity.TestFolder;
import com.example.app.modules.testrepository.exception.TestFolderNotFoundException;
import com.example.app.modules.testrepository.repository.TestFolderRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestCaseImportServiceImpl implements TestCaseImportService {

    private static final int PREVIEW_LIMIT = 20;

    private final TestCaseRepository testCaseRepository;     // -> datasource "frontline"
    private final TestFolderRepository testFolderRepository; // -> datasource "frontline"
    private final ProjectAccessService projectAccessService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public TestCaseImportResultDTO importTestCases(
            String userEmail, UUID projectId, UUID folderId, MultipartFile file, boolean dryRun
    ) {
        // Aturan akses SAMA dgn createTestCase: OWNER maupun COLLABORATOR.
        ProjectCollaboration myCollaboration = projectAccessService.requireMember(userEmail, projectId);
        Project project = myCollaboration.getProject();

        // Folder wajib ada & milik project ini (404 kalau tidak -- sama seperti createTestCase).
        TestFolder folder = testFolderRepository.findById(folderId)
                .filter(f -> f.getProject().getId().equals(projectId))
                .orElseThrow(TestFolderNotFoundException::new);

        byte[] content = readUpload(file);
        ImportFileGuard.check(file.getOriginalFilename(), content.length, Arrays.copyOf(content, Math.min(4, content.length)));

        ParsedImport parsed = TestCaseExcelParser.parse(new ByteArrayInputStream(content));

        if (parsed.totalRows() == 0) {
            throw new InvalidImportFileException(
                    "Tidak ada baris test case untuk diimpor. Isi data mulai baris 2 pada sheet pertama.");
        }

        boolean hasErrors = !parsed.errors().isEmpty();

        // dryRun, atau ada error: tidak ada yang disimpan.
        if (dryRun || hasErrors) {
            TestCaseImportResultDTO preview = toResult(folder, dryRun, parsed, 0);
            if (!dryRun) {
                // Simpan ditolak total (semua atau tidak sama sekali) -> 400 dengan body hasil yang sama.
                throw new TestCaseImportValidationException(preview);
            }
            return preview;
        }

        List<TestCase> entities = new ArrayList<>(parsed.validRows().size());
        for (ParsedImport.Row row : parsed.validRows()) {
            entities.add(TestCase.builder()
                    .project(project)
                    .folder(folder)
                    .title(row.title())
                    .priority(row.priority())
                    .type(row.type())
                    .scenarioType(row.scenarioType())
                    .description(row.description())
                    .objective(row.objective())
                    .precondition(row.precondition())
                    .testStep(row.testStep())
                    .expectedResult(row.expectedResult())
                    // status default ACTIVE (@Builder.Default); id dibuat sistem (UUID).
                    .createdBy(userEmail)
                    .updatedBy(userEmail)
                    .build());
        }
        testCaseRepository.saveAll(entities);

        activityLogService.log(userEmail, "IMPORT_TEST_CASES");

        return toResult(folder, false, parsed, entities.size());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] buildTemplate(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);
        return TestCaseImportTemplateBuilder.build();
    }

    private byte[] readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImportFileException("File Excel tidak boleh kosong.");
        }
        // Cek ukuran SEBELUM membaca isinya ke memori.
        if (file.getSize() > ImportFileGuard.MAX_FILE_BYTES) {
            throw new InvalidImportFileException("Ukuran file maksimal 5 MB.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidImportFileException("File tidak bisa dibaca.");
        }
    }

    private TestCaseImportResultDTO toResult(TestFolder folder, boolean dryRun, ParsedImport parsed, int importedCount) {
        List<ImportPreviewRowDTO> preview = parsed.validRows().stream()
                .limit(PREVIEW_LIMIT)
                .map(row -> ImportPreviewRowDTO.builder()
                        .row(row.rowNumber())
                        .title(row.title())
                        .priority(row.priority())
                        .type(row.type())
                        .scenarioType(row.scenarioType())
                        .stepCount(row.stepCount())
                        .build())
                .toList();

        return TestCaseImportResultDTO.builder()
                .folderId(folder.getId())
                .folderName(folder.getFolderName())
                .dryRun(dryRun)
                .totalRows(parsed.totalRows())
                .validRows(parsed.validRows().size())
                .importedCount(importedCount)
                .columnMapping(parsed.columnMapping())
                .unmappedColumns(parsed.unmappedColumns())
                .errors(parsed.errors())
                .warnings(parsed.warnings())
                .preview(preview)
                .build();
    }
}
