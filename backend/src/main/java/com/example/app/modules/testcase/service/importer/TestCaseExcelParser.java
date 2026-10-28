// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/importer/TestCaseExcelParser.java
package com.example.app.modules.testcase.service.importer;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.dto.ImportColumnMappingDTO;
import com.example.app.modules.testcase.dto.ImportIssueDTO;
import com.example.app.modules.testcase.exception.InvalidImportFileException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Membaca & memvalidasi file Excel import test case. TIDAK menyentuh database.
 * <ul>
 *   <li>Hanya SHEET PERTAMA; header di baris 1; urutan kolom bebas (auto-mapping,
 *       lihat {@link ImportColumn}).</li>
 *   <li>Sel formula TIDAK dihitung -- yang dibaca hanya nilai hasil terakhir
 *       yang tersimpan di file; sel error dianggap kosong.</li>
 *   <li>Baris kosong dilewati; baris contoh dari template (Title diawali
 *       "[CONTOH]") dilewati dengan peringatan supaya tidak ikut masuk.</li>
 *   <li>Semua baris divalidasi dan SEMUA masalahnya dikumpulkan (bukan berhenti
 *       di error pertama) supaya user bisa memperbaiki sekaligus.</li>
 * </ul>
 */
public final class TestCaseExcelParser {

    public static final int MAX_ROWS = 500;
    public static final String EXAMPLE_MARKER = "[CONTOH]";
    private static final int TITLE_MAX_LENGTH = 255;

    // Batas ukuran SETELAH dibuka untuk tiap bagian di dalam .xlsx (sheet, shared strings, dst).
    // Perlindungan zip-bomb bawaan POI hanya menolak rasio kompresi ekstrem (> 100x), jadi file
    // 5 MB yang mengembang 50x (250 MB) lolos dan menghabiskan memori. File 500 baris yang wajar
    // jauh di bawah 1 MB per bagian. (Setelan statis milik POI; aplikasi ini tidak membaca
    // file zip lain -- export Report hanya MENULIS.)
    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;

    static {
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_BYTES);
    }

    private TestCaseExcelParser() {
    }

    public static ParsedImport parse(InputStream input) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new InvalidImportFileException("File Excel tidak memiliki sheet.");
            }
            return parseSheet(workbook.getSheetAt(0));
        } catch (InvalidImportFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Termasuk file rusak dan perlindungan zip-bomb bawaan POI.
            throw new InvalidImportFileException("File tidak bisa dibaca sebagai Excel .xlsx (rusak atau tidak valid).");
        }
    }

    private static ParsedImport parseSheet(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null || isRowBlank(headerRow)) {
            throw new InvalidImportFileException(
                    "Header tidak ditemukan di baris 1 sheet pertama. Gunakan template dari tombol Download Template.");
        }

        // ---- Auto-mapping header ----
        Map<ImportColumn, Integer> columnIndex = new EnumMap<>(ImportColumn.class);
        List<ImportColumnMappingDTO> mapping = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        List<ImportIssueDTO> warnings = new ArrayList<>();

        short lastCell = headerRow.getLastCellNum();
        for (int col = 0; col < lastCell; col++) {
            String header = cellText(headerRow.getCell(col)).strip();
            if (header.isEmpty()) {
                continue;
            }
            ImportColumn matched = ImportColumn.match(header).orElse(null);
            if (matched == null) {
                unmapped.add(header);
            } else if (columnIndex.containsKey(matched)) {
                unmapped.add(header);
                warnings.add(issue(1, matched.templateHeader(),
                        "Kolom '" + header + "' ganda untuk " + matched.templateHeader() + "; kolom pertama yang dipakai."));
            } else {
                columnIndex.put(matched, col);
                mapping.add(ImportColumnMappingDTO.builder().excelColumn(header).field(matched.fieldName()).build());
            }
        }

        List<String> missing = new ArrayList<>();
        for (ImportColumn column : ImportColumn.values()) {
            if (column.required() && !columnIndex.containsKey(column)) {
                missing.add(column.templateHeader());
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidImportFileException("Kolom wajib tidak ditemukan di baris 1: " + String.join(", ", missing)
                    + ". Gunakan template dari tombol Download Template.");
        }

        // ---- Baris data ----
        List<ParsedImport.Row> validRows = new ArrayList<>();
        List<ImportIssueDTO> errors = new ArrayList<>();
        int totalRows = 0;

        for (Row row : sheet) { // hanya baris yang benar-benar ada (baris format-saja tidak ikut)
            if (row.getRowNum() == 0) {
                continue;
            }
            int excelRow = row.getRowNum() + 1;
            Map<ImportColumn, String> values = new LinkedHashMap<>();
            boolean anyValue = false;
            for (Map.Entry<ImportColumn, Integer> entry : columnIndex.entrySet()) {
                String text = cellText(row.getCell(entry.getValue())).strip();
                values.put(entry.getKey(), text);
                anyValue |= !text.isEmpty();
            }
            if (!anyValue) {
                continue;
            }

            String title = values.get(ImportColumn.TITLE);
            if (title.toUpperCase(Locale.ROOT).startsWith(EXAMPLE_MARKER)) {
                warnings.add(issue(excelRow, ImportColumn.TITLE.templateHeader(), "Baris contoh dilewati."));
                continue;
            }

            totalRows++;
            if (totalRows > MAX_ROWS) {
                throw new InvalidImportFileException("Maksimal " + MAX_ROWS
                        + " baris per import. Pecah file menjadi beberapa bagian.");
            }

            // validateRow hanya menambah ke validRows kalau baris ini tidak punya error.
            validateRow(excelRow, values, columnIndex, errors, warnings, validRows);
        }

        return new ParsedImport(validRows, totalRows, mapping, unmapped, errors, warnings);
    }

    private static void validateRow(
            int excelRow,
            Map<ImportColumn, String> values,
            Map<ImportColumn, Integer> columnIndex,
            List<ImportIssueDTO> errors,
            List<ImportIssueDTO> warnings,
            List<ParsedImport.Row> validRows
    ) {
        int errorsBefore = errors.size();

        String title = values.get(ImportColumn.TITLE);
        if (title.isEmpty()) {
            errors.add(issue(excelRow, ImportColumn.TITLE.templateHeader(), "Title wajib diisi."));
        } else if (title.length() > TITLE_MAX_LENGTH) {
            errors.add(issue(excelRow, ImportColumn.TITLE.templateHeader(),
                    "Title maksimal " + TITLE_MAX_LENGTH + " karakter (saat ini " + title.length() + ")."));
        }

        TestCasePriority priority = parseEnum(values.get(ImportColumn.PRIORITY), PRIORITY_VALUES, true,
                excelRow, ImportColumn.PRIORITY, "Highest, High, Medium, atau Low", errors);
        TestCaseType type = parseEnum(values.get(ImportColumn.TYPE), TYPE_VALUES, true,
                excelRow, ImportColumn.TYPE, "Manual atau Automation", errors);
        TestCaseScenarioType scenario = columnIndex.containsKey(ImportColumn.SCENARIO_TYPE)
                ? parseEnum(values.get(ImportColumn.SCENARIO_TYPE), SCENARIO_VALUES, false,
                excelRow, ImportColumn.SCENARIO_TYPE, "Positive atau Negative", errors)
                : null;

        // ---- Test step & expected results: sejajar per baris ----
        List<String> steps = TestStepSplitter.split(values.getOrDefault(ImportColumn.TEST_STEP, ""));
        List<String> expected = TestStepSplitter.split(values.getOrDefault(ImportColumn.EXPECTED_RESULT, ""));
        String testStep = null;
        String expectedResult = null;
        int stepCount = steps.size();

        if (!steps.isEmpty() || !expected.isEmpty()) {
            int size = Math.max(steps.size(), expected.size());
            if (!steps.isEmpty() && !expected.isEmpty() && steps.size() != expected.size()) {
                warnings.add(issue(excelRow, ImportColumn.TEST_STEP.templateHeader(),
                        "Jumlah step (" + steps.size() + ") tidak sama dengan expected results (" + expected.size()
                                + "); baris kosong ditambahkan agar sejajar."));
            }
            if (steps.isEmpty()) {
                warnings.add(issue(excelRow, ImportColumn.EXPECTED_RESULT.templateHeader(),
                        "Expected results diisi tetapi Test step kosong; expected results tetap disimpan."));
                stepCount = 0;
                testStep = null;
            } else {
                testStep = TestStepSplitter.number(steps, size);
            }
            // Hanya step yang diisi (tanpa expected) -> expected dibiarkan kosong, bukan baris kosong semua.
            expectedResult = expected.isEmpty() ? null : TestStepSplitter.number(expected, size);
            if (!steps.isEmpty()) {
                stepCount = size;
            }
        }

        if (errors.size() > errorsBefore) {
            return;
        }

        validRows.add(new ParsedImport.Row(
                excelRow,
                title,
                priority,
                type,
                scenario,
                blankToNull(values.get(ImportColumn.DESCRIPTION)),
                blankToNull(values.get(ImportColumn.OBJECTIVE)),
                blankToNull(values.get(ImportColumn.PRECONDITION)),
                testStep,
                expectedResult,
                stepCount));
    }

    // Nilai enum yang diterima: lihat TestCaseEnumValues (dipakai bersama draft AI).
    private static final Map<String, TestCasePriority> PRIORITY_VALUES = TestCaseEnumValues.PRIORITY;
    private static final Map<String, TestCaseType> TYPE_VALUES = TestCaseEnumValues.TYPE;
    private static final Map<String, TestCaseScenarioType> SCENARIO_VALUES = TestCaseEnumValues.SCENARIO;

    private static <E> E parseEnum(
            String raw, Map<String, E> allowed, boolean required,
            int excelRow, ImportColumn column, String acceptedHint, List<ImportIssueDTO> errors
    ) {
        if (raw == null || raw.isEmpty()) {
            if (required) {
                errors.add(issue(excelRow, column.templateHeader(), column.templateHeader() + " wajib diisi."));
            }
            return null;
        }
        E value = allowed.get(ImportColumn.normalize(raw));
        if (value == null) {
            errors.add(issue(excelRow, column.templateHeader(),
                    "Nilai '" + raw + "' tidak dikenal. Gunakan " + acceptedHint + "."));
        }
        return value;
    }

    // ---- Helper sel ----

    private static boolean isRowBlank(Row row) {
        for (Cell cell : row) {
            if (!cellText(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Teks sel tanpa mengeksekusi apa pun: formula -> nilai hasil terakhir yang
     * tersimpan di file (bukan dihitung ulang); error/kosong -> "".
     */
    static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> numericText(cell);
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> numericText(cell);
                case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
                default -> "";
            };
            default -> "";
        };
    }

    private static String numericText(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return NumberToTextConverter.toText(cell.getNumericCellValue()); // 1.0 -> "1", 2.5 -> "2.5"
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static ImportIssueDTO issue(int row, String column, String message) {
        return ImportIssueDTO.builder().row(row).column(column).message(message).build();
    }
}
