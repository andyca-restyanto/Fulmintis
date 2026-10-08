// filepath: /backend/src/test/java/com/example/app/modules/testcase/service/TestCaseExcelParserTest.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.dto.ImportIssueDTO;
import com.example.app.modules.testcase.exception.InvalidImportFileException;
import com.example.app.modules.testcase.service.ExcelTestData.Formula;
import com.example.app.modules.testcase.service.importer.ParsedImport;
import com.example.app.modules.testcase.service.importer.TestCaseExcelParser;
import org.junit.jupiter.api.Test;

import org.apache.poi.ss.usermodel.Row;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseExcelParserTest {

    private static final List<String> HEADER = List.of("Title", "Priority", "Test type", "Scenario type",
            "Description", "Objective", "Pre-condition", "Test step", "Expected results");

    private static ParsedImport parse(byte[] xlsx) {
        return TestCaseExcelParser.parse(new ByteArrayInputStream(xlsx));
    }

    private static List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static boolean hasIssue(List<ImportIssueDTO> issues, int row, String column, String messagePart) {
        return issues.stream().anyMatch(i -> i.getRow() == row && i.getColumn().equals(column)
                && i.getMessage().contains(messagePart));
    }

    // ---- Kasus normal ----

    @Test
    void parsesAFullyFilledRowWithAllNineColumns() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(row(
                "Login berhasil", "High", "Manual", "Positive", "Deskripsi", "Tujuan", "User terdaftar",
                "1. Buka login\n2. Isi form\n3. Klik Sign In", "1. Form tampil\n2. Terisi\n3. Masuk dashboard"))));

        assertEquals(1, parsed.totalRows());
        assertTrue(parsed.errors().isEmpty());
        assertTrue(parsed.warnings().isEmpty());
        assertEquals(9, parsed.columnMapping().size());
        assertTrue(parsed.unmappedColumns().isEmpty());

        ParsedImport.Row r = parsed.validRows().get(0);
        assertEquals(2, r.rowNumber()); // header = baris 1, data pertama = baris 2
        assertEquals("Login berhasil", r.title());
        assertEquals(TestCasePriority.HIGH, r.priority());
        assertEquals(TestCaseType.MANUAL, r.type());
        assertEquals(TestCaseScenarioType.POSITIVE, r.scenarioType());
        assertEquals("Deskripsi", r.description());
        assertEquals("Tujuan", r.objective());
        assertEquals("User terdaftar", r.precondition());
        assertEquals("1. Buka login\n2. Isi form\n3. Klik Sign In", r.testStep());
        assertEquals("1. Form tampil\n2. Terisi\n3. Masuk dashboard", r.expectedResult());
        assertEquals(3, r.stepCount());
    }

    @Test
    void optionalColumnsMayBeMissingFromTheFileAndCellsMayBeBlank() {
        ParsedImport parsed = parse(ExcelTestData.workbook(List.of("Title", "Priority", "Test type"),
                List.of(row("Hanya wajib", "low", "automation"))));

        ParsedImport.Row r = parsed.validRows().get(0);
        assertNull(r.scenarioType());
        assertNull(r.description());
        assertNull(r.testStep());
        assertNull(r.expectedResult());
        assertEquals(0, r.stepCount());
        assertTrue(parsed.errors().isEmpty());
    }

    @Test
    void autoMapsShuffledColumnsAndVariantHeaderNamesAndReportsUnknownOnes() {
        List<String> header = List.of("expected RESULT", "Pre condition", "Judul", "PRIORITAS", "Test-Type", "Owner", "Test steps");
        ParsedImport parsed = parse(ExcelTestData.workbook(header, List.of(
                row("1. ok", "Akun ada", "Cek judul", "Highest", "Manual", "andi", "1. klik"))));

        assertTrue(parsed.errors().isEmpty());
        ParsedImport.Row r = parsed.validRows().get(0);
        assertEquals("Cek judul", r.title());
        assertEquals(TestCasePriority.HIGHEST, r.priority());
        assertEquals("Akun ada", r.precondition());
        assertEquals("1. klik", r.testStep());
        assertEquals("1. ok", r.expectedResult());
        assertEquals(List.of("Owner"), parsed.unmappedColumns());
        // excelColumn memuat teks header ASLI user, field = nama field sistem
        assertTrue(parsed.columnMapping().stream()
                .anyMatch(m -> m.getExcelColumn().equals("Judul") && m.getField().equals("title")));
        assertTrue(parsed.columnMapping().stream()
                .anyMatch(m -> m.getExcelColumn().equals("Pre condition") && m.getField().equals("precondition")));
    }

    @Test
    void enumValuesAreCaseAndSpaceInsensitiveWithCommonSynonyms() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(
                row("a", "  HIGHEST ", "AUTOMATED", "negatif"),
                row("b", "low", "otomatis", "POSITIVE"))));

        assertTrue(parsed.errors().isEmpty());
        assertEquals(TestCasePriority.HIGHEST, parsed.validRows().get(0).priority());
        assertEquals(TestCaseType.AUTOMATION, parsed.validRows().get(0).type());
        assertEquals(TestCaseScenarioType.NEGATIVE, parsed.validRows().get(0).scenarioType());
        assertEquals(TestCaseType.AUTOMATION, parsed.validRows().get(1).type());
    }

    // ---- Baris & sel ----

    @Test
    void skipsBlankRowsAndKeepsRealExcelRowNumbers() {
        Map<Integer, List<Object>> rows = new LinkedHashMap<>();
        rows.put(1, row("Pertama", "High", "Manual"));
        rows.put(4, row("Kedua", "Low", "Manual")); // baris 2-3 (indeks) kosong -> Excel baris 5
        ParsedImport parsed = parse(ExcelTestData.workbookWithRowIndexes(HEADER, rows));

        assertEquals(2, parsed.totalRows());
        assertEquals(2, parsed.validRows().get(0).rowNumber());
        assertEquals(5, parsed.validRows().get(1).rowNumber());
    }

    @Test
    void readsNumbersDatesAndFormulaCachedValuesAsPlainText() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(row(
                "Sel campuran", "High", "Manual", null,
                123, // numerik 123.0 -> "123", bukan "123.0"
                LocalDate.of(2026, 10, 5),
                new Formula("1+1")))));

        ParsedImport.Row r = parsed.validRows().get(0);
        assertEquals("123", r.description());
        assertEquals("2026-10-05", r.objective());
        assertEquals("2", r.precondition()); // nilai hasil formula yang tersimpan, bukan teks "=1+1"
    }

    @Test
    void formulaLikeTextIsKeptAsDataNotExecuted() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(
                row("=HYPERLINK(\"http://x\")", "High", "Manual"))));

        // sel bertipe STRING yang isinya diawali "=" adalah data biasa; tidak pernah dievaluasi
        assertEquals("=HYPERLINK(\"http://x\")", parsed.validRows().get(0).title());
    }

    @Test
    void templateExampleRowIsSkippedWithAWarningAndNotCounted() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(
                row("[CONTOH] Login berhasil", "High", "Manual"),
                row("Test sungguhan", "High", "Manual"))));

        assertEquals(1, parsed.totalRows());
        assertEquals(1, parsed.validRows().size());
        assertTrue(hasIssue(parsed.warnings(), 2, "Title", "contoh dilewati"));
    }

    // ---- Validasi ----

    @Test
    void invalidEnumReportsExcelRowColumnAndAcceptedValues() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(
                row("ok", "High", "Manual"),
                row("ok2", "High", "Manual"),
                row("ok3", "High", "Manual"),
                row("Bermasalah", "Urgent", "Manual"))));

        assertEquals(4, parsed.totalRows());
        assertEquals(3, parsed.validRows().size());
        assertEquals(1, parsed.errors().size());
        assertTrue(hasIssue(parsed.errors(), 5, "Priority", "'Urgent' tidak dikenal"));
        assertTrue(hasIssue(parsed.errors(), 5, "Priority", "Highest, High, Medium, atau Low"));
    }

    @Test
    void collectsEveryProblemOfARowAndAcrossRowsInsteadOfStoppingAtTheFirst() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(
                row(null, "Urgent", "Hybrid", "Neutral", "desc"),
                row("x".repeat(256), "High", "Manual"),
                row("Lengkap tapi Priority kosong", null, "Manual"))));

        assertEquals(0, parsed.validRows().size());
        assertTrue(hasIssue(parsed.errors(), 2, "Title", "wajib diisi"));
        assertTrue(hasIssue(parsed.errors(), 2, "Priority", "'Urgent'"));
        assertTrue(hasIssue(parsed.errors(), 2, "Test type", "'Hybrid'"));
        assertTrue(hasIssue(parsed.errors(), 2, "Scenario type", "'Neutral'"));
        assertTrue(hasIssue(parsed.errors(), 3, "Title", "maksimal 255"));
        assertTrue(hasIssue(parsed.errors(), 4, "Priority", "wajib diisi"));
    }

    // ---- Test step & expected results ----

    @Test
    void mismatchedStepAndExpectedCountsWarnAndPadSoTheyStayAligned() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(row(
                "Tidak sejajar", "High", "Manual", null, null, null, null,
                "a\nb\nc", "x\ny"))));

        ParsedImport.Row r = parsed.validRows().get(0);
        assertEquals("1. a\n2. b\n3. c", r.testStep());
        assertEquals("1. x\n2. y\n3. ", r.expectedResult()); // dilengkapi baris kosong bernomor
        assertEquals(3, r.stepCount());
        assertTrue(hasIssue(parsed.warnings(), 2, "Test step", "Jumlah step (3) tidak sama dengan expected results (2)"));
    }

    @Test
    void onlyStepsFilledMeansNoExpectedResultAndNoWarning() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(row(
                "Hanya step", "High", "Manual", null, null, null, null, "satu\ndua", null))));

        ParsedImport.Row r = parsed.validRows().get(0);
        assertEquals("1. satu\n2. dua", r.testStep());
        assertNull(r.expectedResult());
        assertTrue(parsed.warnings().isEmpty());
    }

    @Test
    void onlyExpectedFilledWarnsButIsStillSaved() {
        ParsedImport parsed = parse(ExcelTestData.workbook(HEADER, List.of(row(
                "Hanya expected", "High", "Manual", null, null, null, null, null, "hasil"))));

        ParsedImport.Row r = parsed.validRows().get(0);
        assertNull(r.testStep());
        assertEquals("1. hasil", r.expectedResult());
        assertEquals(0, r.stepCount());
        assertTrue(hasIssue(parsed.warnings(), 2, "Expected results", "Test step kosong"));
    }

    // ---- File bermasalah ----

    @Test
    void missingRequiredColumnsFailTheWholeFileAndNameTheMissingOnes() {
        InvalidImportFileException e = assertThrows(InvalidImportFileException.class,
                () -> parse(ExcelTestData.workbook(List.of("Title", "Description"), List.of(row("a", "b")))));

        assertTrue(e.getMessage().contains("Priority"));
        assertTrue(e.getMessage().contains("Test type"));
        assertFalse(e.getMessage().contains("Title,"));
    }

    @Test
    void duplicateHeaderIsWarnedAndFirstColumnWins() {
        ParsedImport parsed = parse(ExcelTestData.workbook(
                List.of("Title", "Priority", "Test type", "Title"),
                List.of(row("Pertama", "High", "Manual", "Kedua"))));

        assertEquals("Pertama", parsed.validRows().get(0).title());
        assertTrue(hasIssue(parsed.warnings(), 1, "Title", "ganda"));
        assertEquals(List.of("Title"), parsed.unmappedColumns());
    }

    @Test
    void exactly500RowsIsAllowedButMoreIsRejected() {
        List<List<Object>> rows500 = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            rows500.add(row("TC " + i, "Low", "Manual"));
        }
        assertEquals(500, parse(ExcelTestData.workbook(HEADER, rows500)).validRows().size());

        List<List<Object>> rows501 = new ArrayList<>(rows500);
        rows501.add(row("TC 501", "Low", "Manual"));
        InvalidImportFileException e = assertThrows(InvalidImportFileException.class,
                () -> parse(ExcelTestData.workbook(HEADER, rows501)));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void emptyHeaderRowAndGarbageBytesAreRejectedWithAFriendlyMessage() {
        assertThrows(InvalidImportFileException.class,
                () -> parse(ExcelTestData.workbook(List.of(), List.of(row("a", "b", "c")))));
        assertThrows(InvalidImportFileException.class, () -> parse("bukan excel".getBytes()));
        // ZIP valid tetapi bukan workbook Excel
        assertThrows(InvalidImportFileException.class, () -> parse(new byte[]{'P', 'K', 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
    }

    @Test
    void rejectsAFileWhoseContentsExpandPastTheSizeCapEvenWithANormalCompressionRatio() throws Exception {
        // ~25 MB teks acak (heksadesimal): rasio kompresi ~2x -- LOLOS perlindungan rasio POI,
        // tapi satu bagian mengembang > 20 MB dan harus ditolak sebelum memenuhi memori.
        java.util.Random random = new java.util.Random(7);
        byte[] big;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Test Cases");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Title");
            header.createCell(1).setCellValue("Priority");
            header.createCell(2).setCellValue("Test type");
            for (int r = 1; r <= 1000; r++) {
                StringBuilder text = new StringBuilder(26_000);
                for (int i = 0; i < 26_000; i++) {
                    text.append(Integer.toHexString(random.nextInt(16)));
                }
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue(text.toString());
                row.createCell(1).setCellValue("High");
                row.createCell(2).setCellValue("Manual");
            }
            wb.write(out);
            big = out.toByteArray();
        }

        assertThrows(InvalidImportFileException.class, () -> parse(big));
    }
}
