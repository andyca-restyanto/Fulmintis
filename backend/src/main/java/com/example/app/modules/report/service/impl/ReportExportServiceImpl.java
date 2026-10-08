// backend/src/main/java/com/example/app/modules/report/service/impl/ReportExportServiceImpl.java
package com.example.app.modules.report.service.impl;

import com.example.app.modules.report.dto.ReportOverviewResponseDTO;
import com.example.app.modules.report.service.ReportExportService;
import com.example.app.modules.report.service.ReportService;
import com.example.app.modules.testrun.dto.TestResultResponseDTO;
import com.example.app.modules.testrun.dto.TestRunDetailResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generate file .xlsx pakai Apache POI (lihat dependency baru "poi-ooxml" di
 * pom.xml). Semua data diambil dari {@link ReportService} yang SUDAH
 * memvalidasi project & membership -- class ini murni transformasi
 * data -> workbook, tidak ada query DB langsung di sini.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements ReportExportService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReportService reportService;

    @Override
    public byte[] exportProjectReport(String userEmail, UUID projectId) {
        ReportOverviewResponseDTO overview = reportService.getOverview(userEmail, projectId);
        List<TestRunDetailResponseDTO> testRuns = reportService.getRunDetails(userEmail, projectId);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            writeSummarySheet(workbook, headerStyle, overview);
            writeAllResultsSheet(workbook, headerStyle, testRuns);
            writePerRunSheets(workbook, headerStyle, testRuns);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // write() ke ByteArrayOutputStream in-memory -- praktis tidak
            // pernah gagal krn I/O fisik, tapi signature POI tetap throws
            // IOException checked, jadi dibungkus supaya tidak bocor ke
            // caller sbg checked exception (konsisten dgn gaya service lain
            // di app ini yang selalu throws unchecked).
            throw new UncheckedIOException("Gagal generate file Excel report", e);
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }

    private void writeHeaderRow(Sheet sheet, CellStyle headerStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ==================== Sheet "Summary" ====================
    // Kategori & urutan SENGAJA disamakan dgn kartu+distribusi tab Overview
    // (ReportOverviewResponseDTO) -- BUKAN 1:1 dgn contoh file yang diberikan
    // user, yang punya baris "Skipped" terpisah dari "Pending". App ini
    // tidak punya konsep "skipped" (TestResultStatus cuma
    // NEW/PENDING/PASSED/FAILED/BLOCKED, NEW+PENDING sudah digabung jadi
    // "Pending" di ReportOverviewResponseDTO.totalPending sejak tab Overview
    // dibuat) -- menambah baris "Skipped: 0" yang selalu 0 dianggap lebih
    // menyesatkan (seolah ada fitur skip) drpd konsisten dgn Overview.

    private void writeSummarySheet(XSSFWorkbook workbook, CellStyle headerStyle, ReportOverviewResponseDTO overview) {
        XSSFSheet sheet = workbook.createSheet("Summary");
        writeHeaderRow(sheet, headerStyle, "Metric", "Value");

        writeMetricRow(sheet, 1, "Total Runs", overview.getTotalTestRuns());
        writeMetricRow(sheet, 2, "Total Executions", overview.getTotalExecutions());
        writeMetricRow(sheet, 3, "Passed", overview.getTotalPassed());
        writeMetricRow(sheet, 4, "Failed", overview.getTotalFailed());
        writeMetricRow(sheet, 5, "Blocked", overview.getTotalBlocked());
        writeMetricRow(sheet, 6, "Pending", overview.getTotalPending());
        writeMetricRow(sheet, 7, "Pass Rate (%)", overview.getPassRatePercentage());
        writeMetricRow(sheet, 8, "Execution Rate (%)", overview.getExecutionRatePercentage());
        autoSizeColumns(sheet, 2);

        // Diagram distribusi (requirement #4). Dibungkus try/catch supaya
        // kalau chart API gagal bikin objek chart-nya (mis. behavior aneh di
        // versi POI tertentu), sheet DATA-nya tetap terbentuk & bisa
        // diunduh -- lebih baik file tanpa gambar drpd export gagal total.
        try {
            addDistributionChart(sheet, overview);
        } catch (RuntimeException chartGenerationFailed) {
            log.warn(
                    "Gagal membuat chart distribusi di sheet Summary, lanjut export tanpa chart: {}",
                    chartGenerationFailed.getMessage()
            );
        }
    }

    private void writeMetricRow(Sheet sheet, int rowIndex, String label, long value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue((double) value);
    }

    private void addDistributionChart(XSSFSheet sheet, ReportOverviewResponseDTO overview) {
        // Data chart DIACU ke RANGE SEL di sheet Summary (label di kolom A,
        // angka di kolom B, baris Passed..Pending = index 3..6 sesuai urutan
        // writeMetricRow di writeSummarySheet), BUKAN array literal.
        // XDDFDataSourcesFactory.fromArray() menulis data sbg literal
        // (numLit/strLit) tanpa referensi sel: Excel masih bisa
        // menampilkannya, tapi Google Sheets tidak -- diagram hilang saat
        // file dibuka di sana. Dgn referensi sel (Summary!$A$4:$A$7 dst) POI
        // juga menyimpan cache nilainya, yang dipakai Google Sheets & Excel.
        final int firstRow = 3; // baris "Passed"
        final int lastRow = 6;  // baris "Pending"

        // Anchor: mulai kolom D (index 3) baris 1, kira-kira 7 kolom x 15
        // baris -- diletakkan di samping tabel Metric/Value (kolom A-B)
        // supaya tidak menimpanya.
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, 0, 10, 15);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Overall Execution Distribution");
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(firstRow, lastRow, 0, 0));
        XDDFNumericalDataSource<Double> series1 = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(firstRow, lastRow, 1, 1));

        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        // WAJIB true utk pie chart -- tanpa ini semua slice tampil dgn 1
        // warna yang sama (POI/Excel butuh eksplisit diberitahu utk
        // mewarnai per titik data, bukan per series).
        data.setVaryColors(true);
        XDDFChartData.Series series = data.addSeries(categories, series1);
        series.setTitle("Executions", null);
        chart.plot(data);
    }

    // ==================== Sheet "All Results" ====================
    // Flat: 1 baris per test case per test run, digabung dari SEMUA test
    // run -- format & urutan kolom disamakan PERSIS dgn contoh file yang
    // diberikan user.

    private void writeAllResultsSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<TestRunDetailResponseDTO> testRuns) {
        XSSFSheet sheet = workbook.createSheet("All Results");
        writeHeaderRow(
                sheet, headerStyle,
                "Run Title", "Run Status", "Created At", "Test Case", "Priority", "Type", "Scenario", "Status", "Comment"
        );

        int rowIndex = 1;
        for (TestRunDetailResponseDTO run : testRuns) {
            String runTitle = run.getTitle();
            String runStatus = humanize(run.getStatus());
            String createdAt = run.getCreatedAt() == null ? "" : run.getCreatedAt().format(DATE_TIME_FORMAT);

            for (TestResultResponseDTO result : run.getTestResults()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(runTitle);
                row.createCell(1).setCellValue(runStatus);
                row.createCell(2).setCellValue(createdAt);
                writeTestCaseColumns(row, 3, result);
            }
        }
        autoSizeColumns(sheet, 9);
    }

    // ==================== 1 sheet per test run ====================

    private void writePerRunSheets(XSSFWorkbook workbook, CellStyle headerStyle, List<TestRunDetailResponseDTO> testRuns) {
        Set<String> usedSheetNames = new HashSet<>();
        // "Summary" & "All Results" sudah dipakai duluan -- cegah test run
        // yang titlenya kebetulan sama persis dgn salah satu nama itu.
        usedSheetNames.add("Summary");
        usedSheetNames.add("All Results");

        for (TestRunDetailResponseDTO run : testRuns) {
            String sheetName = uniqueSheetName(usedSheetNames, run.getTitle());
            usedSheetNames.add(sheetName);

            XSSFSheet sheet = workbook.createSheet(sheetName);
            writeHeaderRow(sheet, headerStyle, "Test Case", "Priority", "Type", "Scenario", "Status", "Comment");

            int rowIndex = 1;
            for (TestResultResponseDTO result : run.getTestResults()) {
                Row row = sheet.createRow(rowIndex++);
                writeTestCaseColumns(row, 0, result);
            }
            autoSizeColumns(sheet, 6);
        }
    }

    // ==================== Helper ====================

    /**
     * Tulis 6 kolom Test Case/Priority/Type/Scenario/Status/Comment mulai
     * dari {@code startColumn} -- dipakai sheet "All Results" (mulai kolom
     * 3, setelah Run Title/Run Status/Created At) maupun sheet per-run
     * (mulai kolom 0), supaya urutan & format kedua sheet selalu konsisten.
     * <p>
     * Status di sini SENGAJA ditulis apa adanya per baris (New/Pending/
     * Passed/Failed/Blocked) -- BEDA dgn sheet Summary yang menggabung
     * New+Pending jadi 1 angka "Pending". Status per baris adalah ground
     * truth test case itu SENDIRI (konsisten dgn TestRunExecuteView.vue),
     * penggabungan di Summary murni utk 1 angka ringkasan level project.
     */
    private void writeTestCaseColumns(Row row, int startColumn, TestResultResponseDTO result) {
        row.createCell(startColumn).setCellValue(result.getTestCaseTitle());
        row.createCell(startColumn + 1).setCellValue(humanize(result.getTestCasePriority()));
        row.createCell(startColumn + 2).setCellValue(humanize(result.getTestCaseType()));
        row.createCell(startColumn + 3).setCellValue(
                result.getTestCaseScenarioType() == null ? "" : result.getTestCaseScenarioType().name()
        );
        row.createCell(startColumn + 4).setCellValue(humanize(result.getStatus()));
        row.createCell(startColumn + 5).setCellValue(result.getComment() == null ? "" : result.getComment());
    }

    /**
     * Nama sheet Excel: maks. 31 karakter, tidak boleh berisi
     * {@code : \ / ? * [ ]}, tidak boleh kosong, dan HARUS unik dalam 1
     * workbook (2 test run boleh punya title yang sama persis).
     * {@link WorkbookUtil#createSafeSheetName} hanya menangani 3 aturan
     * pertama -- uniqueness ditangani manual di sini dgn menambah suffix
     * " (2)", " (3)", dst kalau perlu, dipotong lagi kalau hasilnya jadi
     * lebih dari 31 karakter.
     */
    private String uniqueSheetName(Set<String> usedNames, String rawTitle) {
        String base = WorkbookUtil.createSafeSheetName(
                rawTitle == null || rawTitle.isBlank() ? "Test Run" : rawTitle
        );
        String candidate = base;
        int counter = 2;
        while (usedNames.contains(candidate)) {
            String suffix = " (" + counter + ")";
            int maxBaseLength = Math.max(1, 31 - suffix.length());
            String truncatedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
            candidate = truncatedBase + suffix;
            counter++;
        }
        return candidate;
    }

    /** "PASSED" -> "Passed", dst. Dipakai utk semua enum status/priority/type. */
    private String humanize(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String name = value.name();
        return name.substring(0, 1) + name.substring(1).toLowerCase();
    }
}
