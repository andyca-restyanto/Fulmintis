// filepath: /backend/src/test/java/com/example/app/modules/testcase/service/TestCaseImportServiceImplTest.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.project.entity.Project;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.dto.TestCaseImportResultDTO;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.exception.InvalidImportFileException;
import com.example.app.modules.testcase.exception.TestCaseImportValidationException;
import com.example.app.modules.testcase.repository.TestCaseRepository;
import com.example.app.modules.testcase.service.impl.TestCaseImportServiceImpl;
import com.example.app.modules.testcase.service.importer.TestCaseImportTemplateBuilder;
import com.example.app.modules.testrepository.entity.TestFolder;
import com.example.app.modules.testrepository.exception.TestFolderNotFoundException;
import com.example.app.modules.testrepository.repository.TestFolderRepository;
import com.example.app.shared.activitylog.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Perakitan service dgn repository palsu (Proxy): tanpa DB, tanpa Mockito. */
class TestCaseImportServiceImplTest {

    private static final List<String> HEADER = List.of("Title", "Priority", "Test type", "Scenario type",
            "Description", "Objective", "Pre-condition", "Test step", "Expected results");

    private final UUID projectId = UUID.randomUUID();
    private final UUID folderId = UUID.randomUUID();
    private final Project project = Project.builder().id(projectId).build();

    private final List<List<TestCase>> saved = new ArrayList<>();
    private final List<String> activities = new ArrayList<>();

    private TestCaseImportServiceImpl service(UUID folderOwnerProjectId) {
        TestFolder folder = TestFolder.builder()
                .id(folderId).folderName("folder 1").project(Project.builder().id(folderOwnerProjectId).build()).build();

        TestCaseRepository testCases = (TestCaseRepository) Proxy.newProxyInstance(
                TestCaseRepository.class.getClassLoader(), new Class<?>[]{TestCaseRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("saveAll")) {
                        @SuppressWarnings("unchecked")
                        List<TestCase> entities = new ArrayList<>((Iterable<TestCase>) args[0] instanceof List
                                ? (List<TestCase>) args[0] : List.<TestCase>of());
                        saved.add(entities);
                        return entities;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        TestFolderRepository folders = (TestFolderRepository) Proxy.newProxyInstance(
                TestFolderRepository.class.getClassLoader(), new Class<?>[]{TestFolderRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findById")) {
                        return folderId.equals(args[0]) ? Optional.of(folder) : Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        ProjectAccessService access = new ProjectAccessService(null, null) {
            @Override
            public ProjectCollaboration requireMember(String userEmail, UUID pid) {
                return ProjectCollaboration.builder().project(project).build();
            }
        };
        ActivityLogService log = (email, activity) -> activities.add(email + ":" + activity);

        return new TestCaseImportServiceImpl(testCases, folders, access, log);
    }

    private static MultipartFile file(String name, byte[] content) {
        return new MultipartFile() {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return name; }
            public String getContentType() { return null; }
            public boolean isEmpty() { return content.length == 0; }
            public long getSize() { return content.length; }
            public byte[] getBytes() { return content; }
            public InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
            public void transferTo(File dest) throws IOException { throw new UnsupportedOperationException(); }
        };
    }

    private static List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private MultipartFile validFile() {
        return file("cases.xlsx", ExcelTestData.workbook(HEADER, List.of(
                row("Login berhasil", "High", "Manual", "Positive", "d", "o", "p", "a\nb", "x\ny"),
                row("Login gagal", "Highest", "Automation", "Negative"),
                row("Tanpa scenario", "Low", "manual"))));
    }

    @Test
    void dryRunReportsWhatWouldBeImportedAndSavesNothing() {
        TestCaseImportResultDTO result = service(projectId)
                .importTestCases("a@b.com", projectId, folderId, validFile(), true);

        assertTrue(result.isDryRun());
        assertEquals(3, result.getTotalRows());
        assertEquals(3, result.getValidRows());
        assertEquals(0, result.getImportedCount());
        assertEquals("folder 1", result.getFolderName());
        assertEquals(3, result.getPreview().size());
        assertEquals(2, result.getPreview().get(0).getStepCount());
        assertEquals(0, saved.size());          // tidak ada yang disimpan
        assertEquals(0, activities.size());     // dan tidak ada activity log
    }

    @Test
    void savesEveryValidRowIntoTheChosenFolderWithCreatorAndReturnsTheImportedCount() {
        TestCaseImportResultDTO result = service(projectId)
                .importTestCases("a@b.com", projectId, folderId, validFile(), false);

        assertEquals(3, result.getImportedCount());
        assertEquals(1, saved.size());
        List<TestCase> entities = saved.get(0);
        assertEquals(3, entities.size());
        TestCase first = entities.get(0);
        assertEquals("Login berhasil", first.getTitle());
        assertEquals(TestCasePriority.HIGH, first.getPriority());
        assertEquals("1. a\n2. b", first.getTestStep());
        assertEquals("1. x\n2. y", first.getExpectedResult());
        assertEquals("a@b.com", first.getCreatedBy());
        assertEquals("a@b.com", first.getUpdatedBy());
        assertEquals(folderId, first.getFolder().getId());
        assertEquals(projectId, first.getProject().getId());
        assertEquals(List.of("a@b.com:IMPORT_TEST_CASES"), activities);
    }

    @Test
    void anyInvalidRowRejectsTheWholeSaveAndNothingIsStored() {
        MultipartFile bad = file("cases.xlsx", ExcelTestData.workbook(HEADER, List.of(
                row("Baik", "High", "Manual"),
                row("Buruk", "Urgent", "Manual"))));

        TestCaseImportValidationException e = assertThrows(TestCaseImportValidationException.class,
                () -> service(projectId).importTestCases("a@b.com", projectId, folderId, bad, false));

        TestCaseImportResultDTO body = e.getResult();
        assertEquals(1, body.getErrors().size());
        assertEquals(3, body.getErrors().get(0).getRow());
        assertEquals(1, body.getValidRows());
        assertEquals(0, body.getImportedCount());
        assertEquals(0, saved.size());
        assertEquals(0, activities.size());
    }

    @Test
    void dryRunWithBadRowsStillSucceedsAndListsTheErrors() {
        MultipartFile bad = file("cases.xlsx", ExcelTestData.workbook(HEADER, List.of(
                row("Buruk", "Urgent", "Manual"))));

        TestCaseImportResultDTO result = service(projectId)
                .importTestCases("a@b.com", projectId, folderId, bad, true);

        assertEquals(1, result.getErrors().size());
        assertEquals(0, result.getValidRows());
    }

    @Test
    void folderFromAnotherProjectOrUnknownFolderIsNotFound() {
        assertThrows(TestFolderNotFoundException.class, () -> service(UUID.randomUUID())
                .importTestCases("a@b.com", projectId, folderId, validFile(), false));
        assertThrows(TestFolderNotFoundException.class, () -> service(projectId)
                .importTestCases("a@b.com", projectId, UUID.randomUUID(), validFile(), false));
        assertEquals(0, saved.size());
    }

    @Test
    void rejectsWrongOrEmptyFilesBeforeParsing() {
        assertThrows(InvalidImportFileException.class, () -> service(projectId)
                .importTestCases("a@b.com", projectId, folderId, file("cases.csv", "a,b".getBytes()), false));
        assertThrows(InvalidImportFileException.class, () -> service(projectId)
                .importTestCases("a@b.com", projectId, folderId, file("cases.xlsx", "<html>".getBytes()), false));
        assertThrows(InvalidImportFileException.class, () -> service(projectId)
                .importTestCases("a@b.com", projectId, folderId, file("cases.xlsx", new byte[0]), false));
        assertThrows(InvalidImportFileException.class, () -> service(projectId)
                .importTestCases("a@b.com", projectId, folderId, null, false));
        assertEquals(0, saved.size());
    }

    @Test
    void aFileWithOnlyTheTemplateExampleRowHasNothingToImport() {
        InvalidImportFileException e = assertThrows(InvalidImportFileException.class, () -> service(projectId)
                .importTestCases("a@b.com", projectId, folderId,
                        file("template.xlsx", TestCaseImportTemplateBuilder.build()), false));

        assertTrue(e.getMessage().contains("Tidak ada baris"));
    }

    @Test
    void templateDownloadReturnsAnXlsxFile() {
        byte[] template = service(projectId).buildTemplate("a@b.com", projectId);

        assertNotNull(template);
        assertTrue(template.length > 1000);
        assertEquals('P', template[0]);
        assertEquals('K', template[1]);
    }
}
