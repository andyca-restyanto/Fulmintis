// filepath: /backend/src/test/java/com/example/app/modules/testcase/service/ExcelTestData.java
package com.example.app.modules.testcase.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Pembuat file .xlsx kecil untuk test: baris pertama = header, sisanya data. */
final class ExcelTestData {

    private ExcelTestData() {
    }

    /** Header di baris 1, lalu {@code rows} mulai baris 2. Elemen null = sel kosong. */
    static byte[] workbook(List<String> header, List<List<Object>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Test Cases");
            write(sheet, 0, new java.util.ArrayList<Object>(header));
            for (int i = 0; i < rows.size(); i++) {
                write(sheet, i + 1, rows.get(i));
            }
            new XSSFFormulaEvaluator(wb).evaluateAll(); // simpan nilai hasil formula (cached) seperti Excel asli
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Baris data pada indeks fisik tertentu (0-based) -- utk menguji baris kosong di antara data. */
    static byte[] workbookWithRowIndexes(List<String> header, java.util.Map<Integer, List<Object>> rowsByIndex) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Test Cases");
            write(sheet, 0, new java.util.ArrayList<Object>(header));
            rowsByIndex.forEach((index, row) -> write(sheet, index, row));
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Sheet sheet, int rowIndex, List<Object> values) {
        Row row = sheet.createRow(rowIndex);
        for (int col = 0; col < values.size(); col++) {
            Object value = values.get(col);
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(col);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof LocalDate date) {
                cell.setCellValue(date);
                var style = sheet.getWorkbook().createCellStyle();
                style.setDataFormat(sheet.getWorkbook().getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
                cell.setCellStyle(style);
            } else if (value instanceof Formula formula) {
                cell.setCellFormula(formula.expression());
            } else {
                cell.setCellValue(String.valueOf(value));
            }
        }
    }

    /** Penanda sel formula pada data test. */
    record Formula(String expression) {
    }
}
