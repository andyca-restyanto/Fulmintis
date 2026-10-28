// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/importer/TestCaseImportTemplateBuilder.java
package com.example.app.modules.testcase.service.importer;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Template import: sheet "Test Cases" (header, 1 baris contoh, dropdown utk
 * Priority/Test type/Scenario type yang nilainya diambil dari enum sistem
 * sehingga selalu sinkron) + sheet "Petunjuk". Baris contoh berjudul
 * "[CONTOH] ..." dan DILEWATI saat import, jadi template yang tidak diubah
 * strukturnya aman diimpor tanpa menyisipkan data contoh.
 */
public final class TestCaseImportTemplateBuilder {

    public static final String SHEET_NAME = "Test Cases";
    private static final int[] COLUMN_WIDTH_CHARS = {34, 12, 14, 16, 36, 30, 30, 46, 46};

    private TestCaseImportTemplateBuilder() {
    }

    public static byte[] build() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            ImportColumn[] columns = ImportColumn.values();

            // ---- Style ----
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle requiredHeader = headerStyle(workbook, boldFont, IndexedColors.LIGHT_YELLOW);
            CellStyle optionalHeader = headerStyle(workbook, boldFont, IndexedColors.GREY_25_PERCENT);

            Font grayFont = workbook.createFont();
            grayFont.setItalic(true);
            grayFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            CellStyle exampleStyle = workbook.createCellStyle();
            exampleStyle.setFont(grayFont);
            exampleStyle.setWrapText(true);
            exampleStyle.setVerticalAlignment(VerticalAlignment.TOP);

            // ---- Header ----
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(columns[i].templateHeader());
                cell.setCellStyle(columns[i].required() ? requiredHeader : optionalHeader);
                sheet.setColumnWidth(i, COLUMN_WIDTH_CHARS[i] * 256);
            }

            // ---- Baris contoh (dilewati saat import) ----
            String[] example = {
                    TestCaseExcelParser.EXAMPLE_MARKER + " Login berhasil dengan akun valid",
                    label(TestCasePriority.HIGH.name()),
                    label(TestCaseType.MANUAL.name()),
                    label(TestCaseScenarioType.POSITIVE.name()),
                    "Memastikan user dapat masuk ke aplikasi",
                    "Verifikasi alur login normal",
                    "User sudah terdaftar dan terverifikasi",
                    "1. Buka halaman login\n2. Isi email dan password yang valid\n3. Klik tombol Sign In",
                    "1. Form login tampil\n2. Field terisi\n3. User masuk ke dashboard"
            };
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < example.length; i++) {
                var cell = exampleRow.createCell(i);
                cell.setCellValue(example[i]);
                cell.setCellStyle(exampleStyle);
            }
            exampleRow.setHeightInPoints(62);

            // ---- Dropdown (nilai dari enum sistem) ----
            addDropdown(sheet, 1, labels(TestCasePriority.values()), "Priority");
            addDropdown(sheet, 2, labels(TestCaseType.values()), "Test type");
            addDropdown(sheet, 3, labels(TestCaseScenarioType.values()), "Scenario type");

            sheet.createFreezePane(0, 1);

            writeInstructions(workbook);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat template import.", e);
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook, Font font, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static void addDropdown(Sheet sheet, int columnIndex, String[] values, String columnName) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        // baris 2..(MAX_ROWS + 1) (indeks 1..MAX_ROWS) -- seluruh area data yang diizinkan
        CellRangeAddressList range = new CellRangeAddressList(1, TestCaseExcelParser.MAX_ROWS, columnIndex, columnIndex);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Nilai tidak valid", columnName + " harus salah satu dari: " + String.join(", ", values));
        validation.setSuppressDropDownArrow(true); // kebiasaan POI/XSSF: true = panah dropdown TAMPIL
        sheet.addValidationData(validation);
    }

    private static void writeInstructions(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Petunjuk");
        sheet.setColumnWidth(0, 110 * 256);
        String[] lines = {
                "Cara mengisi template import test case",
                "",
                "1. Isi data di sheet \"" + SHEET_NAME + "\" mulai baris 2 (satu baris = satu test case). Jangan ubah nama kolom di baris 1.",
                "2. Kolom berlatar kuning wajib diisi: Title, Priority, Test type. Kolom lain boleh kosong.",
                "3. Priority: Highest, High, Medium, atau Low.",
                "4. Test type: Manual atau Automation.",
                "5. Scenario type (opsional): Positive atau Negative.",
                "6. Test step dan Expected results: tulis SATU langkah per baris di dalam sel (Alt+Enter di Excel).",
                "   Langkah ke-N di Test step berpasangan dengan baris ke-N di Expected results. Nomor (1., 2., ...) boleh ditulis atau tidak.",
                "7. Baris contoh berjudul \"" + TestCaseExcelParser.EXAMPLE_MARKER + "\" otomatis dilewati; hapus atau biarkan saja.",
                "8. Urutan kolom bebas dan kolom tambahan akan diabaikan. ID test case dibuat otomatis oleh sistem.",
                "9. Maksimal " + TestCaseExcelParser.MAX_ROWS + " baris dan 5 MB per file; format .xlsx.",
                "10. Jika ada satu baris yang tidak valid, seluruh import dibatalkan dan daftar kesalahan per baris ditampilkan."
        };
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle title = workbook.createCellStyle();
        title.setFont(bold);
        CellStyle wrap = workbook.createCellStyle();
        wrap.setWrapText(true);
        for (int i = 0; i < lines.length; i++) {
            var cell = sheet.createRow(i).createCell(0);
            cell.setCellValue(lines[i]);
            cell.setCellStyle(i == 0 ? title : wrap);
        }
    }

    private static String[] labels(Enum<?>[] values) {
        return Arrays.stream(values).map(value -> label(value.name())).toArray(String[]::new);
    }

    /** HIGHEST -> "Highest". */
    private static String label(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }
}
