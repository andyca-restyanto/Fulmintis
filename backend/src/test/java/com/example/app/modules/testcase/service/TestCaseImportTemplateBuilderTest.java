// filepath: /backend/src/test/java/com/example/app/modules/testcase/service/TestCaseImportTemplateBuilderTest.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.service.importer.ParsedImport;
import com.example.app.modules.testcase.service.importer.TestCaseExcelParser;
import com.example.app.modules.testcase.service.importer.TestCaseImportTemplateBuilder;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseImportTemplateBuilderTest {

    @Test
    void templateHasTheNineHeadersAndTheParserMapsAllOfThemWithNoUnknownColumns() {
        ParsedImport parsed = TestCaseExcelParser.parse(new ByteArrayInputStream(TestCaseImportTemplateBuilder.build()));

        assertEquals(9, parsed.columnMapping().size());
        assertTrue(parsed.unmappedColumns().isEmpty());
        List<String> headers = parsed.columnMapping().stream().map(m -> m.getExcelColumn()).toList();
        assertEquals(List.of("Title", "Priority", "Test type", "Scenario type", "Description", "Objective",
                "Pre-condition", "Test step", "Expected results"), headers);
    }

    @Test
    void untouchedTemplateImportsNothingBecauseItsExampleRowIsSkipped() {
        ParsedImport parsed = TestCaseExcelParser.parse(new ByteArrayInputStream(TestCaseImportTemplateBuilder.build()));

        assertEquals(0, parsed.totalRows());
        assertTrue(parsed.validRows().isEmpty());
        assertTrue(parsed.errors().isEmpty());
        assertEquals(1, parsed.warnings().size());
        assertTrue(parsed.warnings().get(0).getMessage().contains("contoh"));
    }

    @Test
    void templateFilledInWithoutChangingItsStructureImportsCleanly() throws Exception {
        byte[] filled;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(TestCaseImportTemplateBuilder.build()));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheetAt(0);
            String[][] data = {
                    {"Login berhasil", "High", "Manual", "Positive", "d", "o", "p", "1. a\n2. b", "1. x\n2. y"},
                    {"Login gagal", "Highest", "Automation", "Negative", null, null, null, null, null}};
            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(2 + i); // baris 3-4 (baris 2 = contoh)
                for (int c = 0; c < data[i].length; c++) {
                    if (data[i][c] != null) {
                        row.createCell(c).setCellValue(data[i][c]);
                    }
                }
            }
            wb.write(out);
            filled = out.toByteArray();
        }

        ParsedImport parsed = TestCaseExcelParser.parse(new ByteArrayInputStream(filled));
        assertEquals(2, parsed.validRows().size());
        assertTrue(parsed.errors().isEmpty());
        assertEquals(3, parsed.validRows().get(0).rowNumber());
        assertEquals("1. a\n2. b", parsed.validRows().get(0).testStep());
    }

    @Test
    void templateHasDropdownsForPriorityTestTypeAndScenarioAndAnInstructionsSheet() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(TestCaseImportTemplateBuilder.build()))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertEquals("Test Cases", wb.getSheetAt(0).getSheetName()); // sheet PERTAMA yang dibaca parser
            assertEquals("Petunjuk", wb.getSheetAt(1).getSheetName());
            assertEquals(3, wb.getSheetAt(0).getDataValidations().size());
        }
    }
}
